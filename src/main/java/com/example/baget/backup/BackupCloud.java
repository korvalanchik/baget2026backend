package com.example.baget.backup;

import com.google.auth.oauth2.GoogleCredentials;
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
 private final String job,credentialsFile;
 public BackupCloud(@Value("${backup.job:}") String job,@Value("${backup.drive-credentials-file:}") String credentialsFile) {this.job=job;this.credentialsFile=credentialsFile;}
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
}
