package com.example.baget.backup;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import com.example.baget.users.UsersRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.baget.calculation.preview.PreviewException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DirectBackupTest {
 @Test void unauthenticatedRequestCannotStartDump() {
  var cloud=mock(BackupCloud.class);
  var controller=new BackupController(new BackupService(mock(JdbcTemplate.class),mock(UsersRepository.class)),cloud);
  assertThrows(PreviewException.class,()->controller.direct(null,new MockHttpServletResponse()));
  verifyNoInteractions(cloud);
 }
 @Test void missingOrForeignServiceUrlFailsBeforeGettingCredentials() {
  for(String url:new String[]{"","http://localhost:8080","https://example.com","https://x.run.app.evil.test","https://x.run.app/dump"})
   assertThrows(PreviewException.class,()->new BackupCloud("","",url).direct());
 }
}
