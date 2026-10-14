package com.example.baget.backup;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;

@Service
public class BackupCloud {
 private final String job,credentialsFile,directUrl;
 public BackupCloud(@Value("${backup.job:}") String job,@Value("${backup.drive-credentials-file:}") String credentialsFile,@Value("${backup.direct-url:}") String directUrl) {this.job=job;this.credentialsFile=credentialsFile;this.directUrl=directUrl.replaceAll("/+$", "");}
 private static String token(GoogleCredentials credentials) throws IOException { credentials.refreshIfExpired(); return credentials.getAccessToken().getTokenValue(); }
 private static HttpURLConnection connection(String url,String token) throws IOException {
  var c=(HttpURLConnection) URI.create(url).toURL().openConnection();c.setConnectTimeout(10000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Authorization","Bearer "+token);return c;
 }
 public boolean launch() {
  // A persisted queue entry survives a failed invocation; Scheduler will pick it up.
  if(!job.matches("projects/[A-Za-z0-9_-]+/locations/[a-z0-9-]+/jobs/[a-z0-9-]+")) return false;
  HttpURLConnection c=null;
  try {
   var credentials=GoogleCredentials.getApplicationDefault().createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
   c=connection("https://run.googleapis.com/v2/"+job+":run",token(credentials));
   c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
   try(var out=c.getOutputStream()){out.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   return c.getResponseCode()/100==2;
  } catch(IOException e) {return false;} finally {if(c!=null)c.disconnect();}
 }
 public InputStream download(String fileId) throws IOException {
  if(credentialsFile.isBlank() || fileId==null || !fileId.matches("[A-Za-z0-9_-]+")) throw new IOException("Backup storage unavailable");
  GoogleCredentials credentials;
  try(var in=Files.newInputStream(Path.of(credentialsFile))){credentials=GoogleCredentials.fromStream(in);}
  var c=connection("https://www.googleapis.com/drive/v3/files/"+fileId+"?alt=media",token(credentials));
  try {
   if(c.getResponseCode()!=200) throw new IOException("Backup download unavailable");
   return new FilterInputStream(c.getInputStream()) { @Override public void close() throws IOException {try {super.close();}finally {c.disconnect();}} };
  } catch(IOException e) {c.disconnect();throw e;}
 }
 public record DirectFile(InputStream stream,long size,String sha256) implements AutoCloseable {
  @Override public void close() throws IOException {stream.close();}
 }
 public DirectFile direct() {
  // Configuration only, never a URL supplied by the browser.
  if(!directUrl.matches("https://[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.run\\.app"))
   throw new PreviewException(HttpStatus.CONFLICT,"BACKUP_DIRECT_CONFIG","Пряме копіювання ще не підключено. Налаштуйте захищений сервіс і BACKUP_DIRECT_URL");
  HttpURLConnection c=null;
  try {
   var credentials=GoogleCredentials.getApplicationDefault();
   if(!(credentials instanceof IdTokenProvider provider)) throw new IOException("ID token unavailable");
   var identity=IdTokenCredentials.newBuilder().setIdTokenProvider(provider).setTargetAudience(directUrl).build();
   identity.refreshIfExpired();
   c=connection(directUrl+"/dump",identity.getAccessToken().getTokenValue());
   c.setReadTimeout(3300000);c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(0);
   c.getOutputStream().close();
   int status=c.getResponseCode();
   if(status==409) throw new PreviewException(HttpStatus.CONFLICT,"BACKUP_BUSY","Інша копія вже створюється. Спробуйте пізніше");
   if(status!=200) throw new IOException("Direct backup unavailable");
   long size=Long.parseLong(c.getHeaderField("X-Backup-Size"));
   String sha=c.getHeaderField("X-Backup-SHA256");
   if(size<=0 || sha==null || !sha.matches("[a-f0-9]{64}")) throw new IOException("Invalid backup metadata");
   final var active=c;
   return new DirectFile(new FilterInputStream(c.getInputStream()) {
    @Override public void close() throws IOException {try{super.close();}finally{active.disconnect();}}
   },size,sha);
  } catch(PreviewException e) {if(c!=null)c.disconnect();throw e;}
  catch(IOException|IllegalArgumentException e) {
   if(c!=null)c.disconnect();
   throw new PreviewException(HttpStatus.CONFLICT,"BACKUP_DIRECT_FAILED","Не вдалося створити пряму копію. Перевірте налаштування сервісу, доступ до MySQL та таймаути Cloud Run");
  }
 }

}
