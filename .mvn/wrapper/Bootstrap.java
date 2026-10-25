/* J-Harness pinned Maven bootstrap. Original wrapper launchers are Apache-2.0. */
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;

/** JDK-only bootstrap: the retained, pinned ZIP authenticates every cached file on each run. */
public final class Bootstrap {
    private static final long ZIP_LIMIT=32L*1024*1024, EXPANDED_LIMIT=128L*1024*1024;
    private static String hash(Path file) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){
            byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;)digest.update(buffer,0,n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void regular(Path file) throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Maven cache file is not regular");
    }
    private static Map<String,String> contents(Path zip, String checksum, Path destination) throws Exception {
        regular(zip);
        if(Files.size(zip)>ZIP_LIMIT || !hash(zip).equals(checksum))throw new IOException("Maven ZIP checksum mismatch");
        var files=new TreeMap<String,String>();long bytes=0;int entries=0;
        try(var input=new ZipInputStream(Files.newInputStream(zip))){
            for(ZipEntry entry;(entry=input.getNextEntry())!=null;){
                if(++entries>4096)throw new IOException("Maven ZIP entry limit");
                String name=entry.getName();
                if(!name.startsWith("apache-maven-3.9.9/") || name.contains("\\") || name.contains(":"))throw new IOException("Maven ZIP path");
                String relative=name.substring("apache-maven-3.9.9/".length());
                if(relative.isEmpty()&&entry.isDirectory())continue;
                String normalized=relative.endsWith("/")?relative.substring(0,relative.length()-1):relative;
                if(normalized.isEmpty() || Arrays.stream(normalized.split("/",-1)).anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new IOException("Maven ZIP path");
                Path target=destination==null?null:destination.resolve(normalized);
                if(entry.isDirectory()){if(target!=null)Files.createDirectories(target);continue;}
                var digest=MessageDigest.getInstance("SHA-256");
                if(files.containsKey(relative))throw new IOException("Duplicate Maven ZIP file");
                if(target!=null)Files.createDirectories(target.getParent());
                try(var out=target==null?OutputStream.nullOutputStream():Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
                    byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))>=0;){
                        bytes+=n;if(bytes>EXPANDED_LIMIT)throw new IOException("Maven ZIP expansion limit");
                        digest.update(buffer,0,n);out.write(buffer,0,n);
                    }
                }
                files.put(relative,HexFormat.of().formatHex(digest.digest()));
            }
        }
        if(!files.containsKey("bin/mvn")||!files.containsKey("bin/mvn.cmd"))throw new IOException("Maven executable missing");
        return files;
    }
    static void verify(Path home,String checksum) throws Exception {
        if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid Maven cache root");
        regular(home.resolve(".complete"));
        var expected=contents(home.resolve(".distribution.zip"),checksum,null);
        if(!Files.readString(home.resolve(".complete")).equals(checksum))throw new IOException("Incomplete Maven cache");
        var actual=new TreeSet<String>();
        try(var walk=Files.walk(home)){
            for(Path path:walk.toList()){
                if(Files.isSymbolicLink(path))throw new IOException("Linked Maven cache");
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;
                regular(path);String relative=home.relativize(path).toString().replace('\\','/');
                if(relative.equals(".distribution.zip")||relative.equals(".complete"))continue;
                actual.add(relative);
                if(!Objects.equals(expected.get(relative),hash(path)))throw new IOException("Maven cached file changed");
            }
        }
        if(!actual.equals(expected.keySet()))throw new IOException("Maven cached file set changed");
    }
    static void install(Path zip,Path home,String checksum) throws Exception {
        Path parent=home.toAbsolutePath().getParent();Files.createDirectories(parent);
        Path staging=Files.createTempDirectory(parent,".maven-install-");
        try{
            contents(zip,checksum,staging);
            Files.copy(zip,staging.resolve(".distribution.zip"));
            Files.writeString(staging.resolve(".complete"),checksum,StandardOpenOption.CREATE_NEW);
            staging.resolve("bin/mvn").toFile().setExecutable(true,false);
            verify(staging,checksum);
            try{Files.move(staging,home,StandardCopyOption.ATOMIC_MOVE);}
            catch(IOException occupied){if(!Files.exists(home))throw occupied;verify(home,checksum);}
        } finally {
            // Staging is a freshly created directory owned by this invocation, never a computed cache root.
            if(Files.exists(staging))try(var walk=Files.walk(staging)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
    private static final class LimitedBody implements java.net.http.HttpResponse.BodySubscriber<byte[]> {
        private final java.net.http.HttpResponse.BodySubscriber<byte[]> delegate=java.net.http.HttpResponse.BodySubscribers.ofByteArray();
        private java.util.concurrent.Flow.Subscription subscription; private long bytes;
        public java.util.concurrent.CompletionStage<byte[]> getBody(){return delegate.getBody();}
        public void onSubscribe(java.util.concurrent.Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}
        public void onNext(List<java.nio.ByteBuffer> buffers){
            for(var buffer:buffers)bytes+=buffer.remaining();
            if(bytes>ZIP_LIMIT){subscription.cancel();delegate.onError(new IOException("Maven download limit"));}else delegate.onNext(buffers);
        }
        public void onError(Throwable error){delegate.onError(error);}
        public void onComplete(){delegate.onComplete();}
    }
    public static void main(String[] args) throws Exception {
        if(Runtime.version().feature()<21)throw new IOException("Maven bootstrap requires JDK 21 or newer");
        if(args.length!=1)throw new IOException("Expected repository root");
        var props=new Properties();try(var input=Files.newInputStream(Path.of(args[0],".mvn/wrapper/maven-wrapper.properties"))){props.load(input);}
        String url=props.getProperty("distributionUrl"),checksum=props.getProperty("distributionSha256Sum");
        if(checksum==null||!checksum.matches("[0-9a-f]{64}")||url==null||!url.endsWith("/apache-maven-3.9.9-bin.zip"))throw new IOException("Unsupported Maven distribution configuration");
        String mirror=System.getenv("MVNW_REPOURL");
        if(mirror!=null&&!mirror.isEmpty()){
            String pattern="/org/apache/maven/";int at=url.indexOf(pattern);
            if(at<0)throw new IOException("Invalid Maven repository pattern");
            url=mirror.replaceAll("/+$","")+url.substring(at);
        }
        URI uri=URI.create(url);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IOException("Maven URL must be public HTTPS without credentials/query/fragment");
        if(System.getenv("MVNW_USERNAME")!=null||System.getenv("MVNW_PASSWORD")!=null)throw new IOException("Authenticated bootstrap mirrors are unsupported; use a public pinned HTTPS mirror");
        String userHome=System.getenv("MAVEN_USER_HOME");
        Path cache=Path.of(userHome==null?System.getProperty("user.home")+"/.m2":userHome,"wrapper/dists/apache-maven-3.9.9");
        String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((url+checksum).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Path home=cache.resolve("verified-"+key);
        if(!Files.exists(home)){
            Files.createDirectories(cache);Path download=Files.createTempFile(cache,".maven-download-",".zip");
            try{
                var client=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
                var request=java.net.http.HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90)).GET().build();
                var future=client.sendAsync(request,info -> new LimitedBody());
                try {
                    var response=future.get(90,java.util.concurrent.TimeUnit.SECONDS);
                    if(response.statusCode()!=200)throw new IOException("Maven download HTTP failure");
                    Files.write(download,response.body());
                } finally { future.cancel(true); }
                install(download,home,checksum);
            }finally{Files.deleteIfExists(download);}
        }
        verify(home,checksum);
        System.out.println(home.toAbsolutePath());
    }
}
