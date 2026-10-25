import io.jharness.api.*;
import java.util.*;
import java.lang.management.ManagementFactory;
public class SanitizerCost {
  public static void main(String[] ignored) {
    var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    var sanitizer = new SecretRedactor(List.of("synthetic-known-secret"));
    var chained = DataSanitizer.chain(sanitizer, sanitizer);
    for (int size : new int[]{100,1000}) {
      var rows = new ArrayList<Map<String,Object>>();
      for (int i=0;i<size;i++) rows.add(Map.of("id",i,"text","bounded sample content","ok",true));
      var payload = Map.<String,Object>of("rows",rows);
      for (int i=0;i<10;i++) chained.sanitize(payload);
      for (var mode : List.of("single","chain")) {
        DataSanitizer operation = mode.equals("chain") ? chained : sanitizer;
        long thread=Thread.currentThread().threadId(), allocation=bean.getThreadAllocatedBytes(thread), start=System.nanoTime();
        for (int i=0;i<25;i++) if(operation.sanitize(payload).size()!=1) throw new AssertionError();
        long elapsed=System.nanoTime()-start, bytes=bean.getThreadAllocatedBytes(thread)-allocation;
        System.out.println("{\"rows\":"+size+",\"iterations\":25,\"mode\":\""+mode+"\",\"elapsedNanos\":"+elapsed+",\"threadAllocatedBytes\":"+bytes+"}");
      }
    }
  }
}
