package vip.xiaonuo.srm.workspace;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.*;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import io.minio.*;
@Component
public class SrmPrivateStorage {
 private final Path root;private final String engine,bucket;private final MinioClient minio;
 public SrmPrivateStorage(Environment e) throws Exception{
  root=Path.of(e.getProperty("srm.storage.directory",".runtime/srm-files")).toAbsolutePath().normalize();
  engine=e.getProperty("SRM_STORAGE_ENGINE","LOCAL");
  if(!java.util.Set.of("LOCAL","MINIO").contains(engine))throw new IllegalStateException("Unknown SRM storage engine");
  String endpoint=e.getProperty("SRM_MINIO_ENDPOINT");bucket=e.getProperty("SRM_MINIO_BUCKET","srm-private");
  minio=endpoint==null?null:MinioClient.builder().endpoint(endpoint).credentials(e.getRequiredProperty("SRM_MINIO_ACCESS_KEY"),e.getRequiredProperty("SRM_MINIO_SECRET_KEY")).build();
  if(engine.equals("MINIO")&&minio==null)throw new IllegalStateException("MinIO storage requires explicit configuration");
  if(minio!=null && Boolean.parseBoolean(e.getProperty("SRM_MINIO_CREATE_BUCKET","false")) && !minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
 }
 public String engine(){return engine;}
 private String key(String fid){if(!fid.matches("[0-9]{1,20}"))throw new IllegalArgumentException("Invalid storage key");return fid+".blob";}
 public void put(String fid,byte[] bytes) throws Exception{
  if(engine.equals("MINIO")){minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key(fid)).stream(new ByteArrayInputStream(bytes),bytes.length,-1).contentType("application/octet-stream").build());return;}
  Files.createDirectories(root);Path target=root.resolve(key(fid));Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
  try{Files.setPosixFilePermissions(target,PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
 }
 public byte[] get(String fid,String from) throws Exception{
  if(from.equals("MINIO")){if(minio==null)throw new IllegalStateException("MinIO configuration missing");try(var in=minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key(fid)).build())){return in.readAllBytes();}}
  if(!from.equals("LOCAL"))throw new IllegalArgumentException("Unknown stored engine");return Files.readAllBytes(root.resolve(key(fid)));
 }
 public void delete(String fid,String from) throws Exception{
  if(from.equals("MINIO")){if(minio!=null)minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key(fid)).build());}
  else if(from.equals("LOCAL"))Files.deleteIfExists(root.resolve(key(fid)));
 }
}
