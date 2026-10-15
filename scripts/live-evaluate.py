"""Explicit opt-in paid CLI evaluation. No API key argument/file/output; fixtures only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import threading
import time
import uuid
from decimal import Decimal

ROOT = Path(__file__).resolve().parent.parent

def oracle(directory, kind):
    oracle_dir = ROOT / ".build-cache" / "M4-live" / "oracle"
    oracle_dir.mkdir(parents=True, exist_ok=True)
    source = oracle_dir / "EvaluationOracle.java"
    source.write_text('''public class EvaluationOracle {
      public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("example.Calculator"); Object calculator = c.getConstructor().newInstance();
        Object sum = c.getMethod("add", int.class, int.class).invoke(calculator, 2, 3);
        if (!Integer.valueOf(5).equals(sum)) throw new AssertionError("add behavior");
        if (args[0].equals("calculator")) {
          var multiply = c.getMethod("multiply", int.class, int.class);
          if (multiply.getReturnType() != int.class) throw new AssertionError("multiply return type");
          int[][] cases = {{2,3,6},{-2,3,-6},{0,5,0}};
          for (int[] t : cases) if (!Integer.valueOf(t[2]).equals(multiply.invoke(calculator,t[0],t[1]))) throw new AssertionError("multiply behavior");
        }
        System.out.println("INDEPENDENT ORACLE PASS");
      }
    }''', encoding="utf-8")
    java_home = Path(os.environ["JAVA_HOME"]) / "bin"
    compile = subprocess.run([str(java_home / "javac.exe"), "-d", str(oracle_dir), str(source)], capture_output=True, text=True, timeout=20)
    if compile.returncode != 0:
        raise RuntimeError("Trusted evaluation oracle compilation failed")
    classpath = str(directory / "target" / "classes") + os.pathsep + str(oracle_dir)
    argv = [str(java_home / "java.exe"), "-cp", classpath, "EvaluationOracle", kind]
    check = subprocess.run(argv, capture_output=True, text=True, timeout=10)
    return {"argv": argv, "exitCode": check.returncode, "stdout": check.stdout.strip(), "sourceSha256": hash_file(source), "evidenceKind": "independent trusted driver, separate from model/tool report"}


def hash_file(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def allowed(tool, args, editable, command):
    if tool in ("write_file", "edit_file"):
        path = str(args.get("path", "")).replace("\\", "/")
        text = str(args.get("content", args.get("newText", "")))
        return path in editable and not any(x in text for x in ("ProcessBuilder", "Runtime.", "java.net", "System.getenv", "Files.delete"))
    if tool == "execute_command":
        return args.get("argv") == [command] and args.get("mode") == "CMD" and args.get("cwd", ".") == "."
    # This evaluation uses explicit file edits, never approves arbitrary patch targets/commands.
    return False


def evaluate(kind, repetition, args, total):
    directory = ROOT / ".build-cache" / "M4-live" / f"{kind}-{repetition}-{uuid.uuid4().hex[:8]}"
    directory.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(ROOT / "examples" / kind, directory)
    shutil.copy2(ROOT / "mvnw.cmd", directory / "mvnw.cmd")
    shutil.copy2(ROOT / "mvnw", directory / "mvnw")
    (directory / ".mvn" / "wrapper").mkdir(parents=True)
    shutil.copy2(ROOT / ".mvn" / "wrapper" / "maven-wrapper.properties", directory / ".mvn" / "wrapper" / "maven-wrapper.properties")
    remaining = Decimal(args.limit_cny) - total
    if remaining < Decimal("2.032768"):
        raise RuntimeError("Remaining budget cannot reserve another verified 1M-token request bound")
    config = {"baseUrl": "https://api.deepseek.com", "model": "deepseek-flash", "apiKeyEnv": "DEEPSEEK_API_KEY", "disableThinking": True,
              "contextTokens": 65536, "maxOutputTokens": 4096, "inputTokenCeiling": 1000000,
              "currency": "CNY", "maxCost": str(remaining), "inputPerMillion": "2", "outputPerMillion": "8",
              "maxSteps": 20, "durationSeconds": 600, "requestTimeoutSeconds": 90}
    config_file = directory / "evaluation.json"
    config_file.write_text(json.dumps(config, ensure_ascii=False), encoding="utf-8")
    command = "call mvnw.cmd -B -o test"
    source = "src/main/java/example/Calculator.java"
    test = "src/test/java/example/CalculatorTest.java"
    if kind == "calculator":
        goal = ("先读取 pom.xml、Calculator.java 和已有测试，说明真实项目结构。给 Calculator 增加 public int multiply(int a,int b) 方法；"
                "修改已有 CalculatorTest.java 加入乘法测试（正数、负数、零），不新建其他测试文件，不修改 pom.xml。"
                "必须通过 execute_command，mode CMD，argv 严格为 [\"call mvnw.cmd -B -o test\"]，cwd 为 . 执行真实测试；失败则修复并重新测试。"
                "最后用中文报告真实变更、测试退出码和局限，不要声称没有执行的操作。文件修改优先使用 read_file 后 edit_file。")
        expected = [source, test]
    else:
        goal = ("先读取 pom.xml 和源码目录确认项目结构，再先运行一次真实测试暴露编译错误；定位并修复 Calculator.java 的编译错误，不修改 pom.xml 或测试。"
                "测试命令必须通过 execute_command，mode CMD，argv 严格为 [\"call mvnw.cmd -B -o test\"]，cwd 为 .。修复后再次运行该命令直到测试通过。"
                "最后用中文报告真实变更、初次错误、实际测试退出码；文件修改优先使用 read_file 后 edit_file。")
        expected = [source]
    java = Path(os.environ["JAVA_HOME"]) / "bin" / "java.exe"
    jar = ROOT / "harness-cli" / "target" / "harness-cli-0.1.0-SNAPSHOT.jar"
    argv = [str(java), "-jar", str(jar), "run", goal, "--workspace", str(directory), "--config", str(config_file), "--session", "evaluation",
            "--require-read", "pom.xml", "--require-read", source, "--verify-argv", json.dumps([command]), "--verify-mode", "CMD"]
    for path in expected:
        argv += ["--expect-file", path]
    process = subprocess.Popen(argv, cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                               encoding="utf-8", errors="replace", creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    lines = []
    messages = queue.Queue()
    def collect():
        for line in process.stdout:
            messages.put(line)
        messages.put(None)
    thread = threading.Thread(target=collect, daemon=True)
    thread.start()
    deadline = time.monotonic() + 660
    try:
        while time.monotonic() < deadline:
            try:
                line = messages.get(timeout=1)
            except queue.Empty:
                continue
            if line is None:
                break
            lines.append(line)
            if line.startswith("APPROVAL "):
                tool = line.split(" tool=", 1)[1].split(" arguments=", 1)[0]
                arguments = json.JSONDecoder().raw_decode(line.split(" arguments=", 1)[1])[0]
                approve = allowed(tool, arguments, set(expected), command)
                process.stdin.write("y\n" if approve else "n\n")
                process.stdin.flush()
                print(f"{kind}#{repetition}: approval {tool} {'approved scoped fixture action' if approve else 'refused out-of-plan action'}", flush=True)
        else:
            states = list((directory / ".jharness" / "runs").glob("*.run"))
            for state in states:
                subprocess.run([str(java), "-jar", str(jar), "cancel", "--workspace", str(directory), "--run", state.stem], cwd=ROOT, capture_output=True, timeout=10)
            raise RuntimeError("Evaluation timed out; cancellation requested, inspect logs before retry")
        process.wait(timeout=15)
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=15)
        process.stdin.close()
        process.stdout.close()
    (directory / "cli-output.log").write_text("".join(lines), encoding="utf-8")
    log = directory / ".jharness" / "sessions" / "evaluation.jsonl"
    events = [json.loads(line) for line in log.read_text(encoding="utf-8").splitlines()[1:]] if log.exists() else []
    budget = [e for e in events if e["type"] in ("budget.reserved", "budget.settled")]
    upper = Decimal(budget[-1]["payload"]["reserved"]) if budget else Decimal(0)
    receipts = [e["payload"].get("usage", {}) for e in budget if e["type"] == "budget.settled"]
    report_paths = list((directory / ".jharness" / "reports").glob("*.json"))
    report = json.loads(report_paths[-1].read_text(encoding="utf-8")) if report_paths else {}
    commands = report.get("recordedFacts", {}).get("commands", [])
    return {"fixture": kind, "repetition": repetition, "directory": str(directory.relative_to(ROOT)), "exitCode": process.returncode,
            "taskSha256": hashlib.sha256(goal.encode("utf-8")).hexdigest(), "cliJarSha256": hash_file(jar), "parameters": config,
            "status": report.get("status", "NO_REPORT"), "reason": report.get("reason", "NO_REPORT"), "requests": sum(e["type"] == "model.requested" for e in events),
            "costUpperBoundCny": str(upper), "usageReceipts": receipts, "commandExitCodes": [c["exitCode"] for c in commands],
            "sourceSha256": {p: hash_file(directory / p) for p in expected if (directory / p).exists()}, "logSha256": hash_file(log) if log.exists() else None,
            "report": str(report_paths[-1].relative_to(ROOT)) if report_paths else None,
            "independentOracle": oracle(directory, kind) if process.returncode == 0 else {"exitCode": -1, "reason": "CLI did not complete"}}, upper


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit-cny", default="18.00")
    parser.add_argument("--repetitions", type=int, default=2)
    parser.add_argument("--audit-only", action="store_true")
    args = parser.parse_args()
    if args.audit_only:
        ledger = ROOT / ".build-cache" / "M4-live" / "ledger.json"
        data = json.loads(ledger.read_text(encoding="utf-8"))
        for run in data["runs"]:
            if run.get("exitCode") == 0:
                directory = (ROOT / run["directory"]).resolve()
                if not directory.is_relative_to((ROOT / ".build-cache" / "M4-live").resolve()):
                    raise RuntimeError("Oracle directory escaped fixture root")
                run["independentOracle"] = oracle(directory, run["fixture"])
                print(f"{run['fixture']}#{run['repetition']}: independent oracle exit {run['independentOracle']['exitCode']}", flush=True)
        ledger.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        (ROOT / "docs/testing/M4-live-results.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        return
    if not os.environ.get("DEEPSEEK_API_KEY"):
        raise RuntimeError("DEEPSEEK_API_KEY is absent; key must be environment-only")
    if Decimal(args.limit_cny) <= 0 or Decimal(args.limit_cny) > Decimal("18.00") or not 1 <= args.repetitions <= 3:
        raise RuntimeError("Evaluation may not exceed authorized CNY 18 or 3 repetitions")
    # Retain earlier ambiguous/failed costs across invocations; never reset the authorized allowance silently.
    ledger = ROOT / ".build-cache" / "M4-live" / "ledger.json"
    ledger.parent.mkdir(parents=True, exist_ok=True)
    previous = json.loads(ledger.read_text(encoding="utf-8")) if ledger.exists() else {"runs": [], "totalUpperBoundCny": "0"}
    total = Decimal(previous["totalUpperBoundCny"])
    runs = previous["runs"]
    recorded = {run["directory"] for run in runs}
    for log in ledger.parent.glob("*/.jharness/sessions/evaluation.jsonl"):
        directory = log.parents[2]
        name = str(directory.relative_to(ROOT))
        if name in recorded:
            continue
        events = []
        for line in log.read_text(encoding="utf-8").splitlines()[1:]:
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError:
                break
        budgets = [e for e in events if e["type"] in ("budget.reserved", "budget.settled")]
        retained = Decimal(budgets[-1]["payload"]["reserved"]) if budgets else Decimal(0)
        total += retained
        runs.append({"directory": name, "status": "INTERRUPTED_COST_RECOVERED", "costUpperBoundCny": str(retained), "logSha256": hash_file(log)})
    ledger.write_text(json.dumps({"runs": runs, "totalUpperBoundCny": str(total)}, ensure_ascii=False, indent=2), encoding="utf-8")
    for repetition in range(1, args.repetitions + 1):
        for kind in ("calculator", "broken-project"):
            run, cost = evaluate(kind, repetition, args, total)
            total += cost
            runs.append(run)
            result = {"provider": "DeepSeek", "model": "deepseek-flash", "baseUrl": "https://api.deepseek.com", "thinking": "disabled", "authorizedLimitCny": args.limit_cny,
                      "inputPerMillionCny": "2", "outputPerMillionCny": "8", "pricingSource": "https://api-docs.deepseek.com/zh-cn/quick_start/pricing/", "totalUpperBoundCny": str(total), "runs": runs,
                      "billingNote": "Server usage at peak/cache-miss prices; unacknowledged calls retain full bounds. This is not a provider invoice."}
            ledger.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            print(json.dumps({k: run[k] for k in ("fixture", "repetition", "exitCode", "status", "reason", "requests", "costUpperBoundCny", "commandExitCodes")}, ensure_ascii=False), flush=True)
            if total > Decimal(args.limit_cny):
                raise RuntimeError("Fee accounting breach; stop all evaluation")
    output = ROOT / "docs" / "testing" / "M4-live-results.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
