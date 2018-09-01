import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.nio.file.FileSystems;
import java.nio.file.Files;

import java.util.Enumeration;
import java.util.Properties;
import java.util.Collections;
import java.util.List;

import java.security.GeneralSecurityException;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import java.text.DecimalFormat;

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.json.JSONObject;


public class Botcoin {

  // Global constants
  private static final String APPLICATION_NAME = "Botcoin";
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static final String TOKENS_DIRECTORY_PATH = "tokens";
  private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_SEND);
  private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

  // Issues "get" request to CoinAPI for ETH-USD exchange rate
  String getRate() throws IOException {
    OkHttpClient client = new OkHttpClient();
    Request request = new Request.Builder()
      .url("https://rest.coinapi.io/v1/exchangerate/ETH/USD")
      .addHeader("Accept", "application/json")
      .addHeader("X-CoinAPI-Key", "931EAC76-F669-4BB6-8AAE-48FF140E2CA3")
      .method("GET", null)
      .build();
    Response response = client.newCall(request).execute();
    return response.body().string();
  }

  // Creates authorized Credential object, implementation taken from: https://developers.google.com/gmail/api/quickstart/java
  private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
      InputStream in = Botcoin.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
      GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
      GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
              HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
              .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
              .setAccessType("offline")
              .build();
      return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
  }

  // Converts email to Gmail "Message" and sends, implementation taken from: https://developers.google.com/gmail/api/v1/reference/users/messages/send
  public static void sendMessage(Gmail service, String userId, MimeMessage email) throws MessagingException, IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    email.writeTo(baos);
    String encodedEmail = Base64.encodeBase64URLSafeString(baos.toByteArray());
    Message message = new Message();
    message.setRaw(encodedEmail);
    message = service.users().messages().send(userId, message).execute();
  }

  public static void main(String... args) throws MessagingException, IOException, GeneralSecurityException {

      // Builds authorized API client service, implementation taken from: https://developers.google.com/gmail/api/quickstart/java
      final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
              .setApplicationName(APPLICATION_NAME)
              .build();

      // Gets rounded ETH-USD exchange rate
      Botcoin eth = new Botcoin();
      String ethRate = eth.getRate();
      JSONObject rateObject = new JSONObject(ethRate);
      Double rate = (Double) rateObject.get("rate");
      DecimalFormat round = new DecimalFormat(".00");
      String roundedRate = "1 ETH = " + round.format(rate) + " USD";

      // Sends rate to recipient(s)
      String user = "me";
      Properties props = new Properties();
      Session session = Session.getDefaultInstance(props, null);
      MimeMessage msg = new MimeMessage(session);
      Address bot = new InternetAddress("eth.price.notifier@gmail.com");
      Address me = new InternetAddress("kodiobika@gmail.com");
      Address liam = new InternetAddress("liamflorian@gmail.com");
      msg.setContent(roundedRate, "text/plain");
      msg.setFrom(bot);
      msg.setRecipient(javax.mail.Message.RecipientType.TO, me);
      msg.addRecipient(javax.mail.Message.RecipientType.TO, liam);
      DateTimeFormatter down = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
      LocalDateTime now = LocalDateTime.now();
      msg.setSubject("ETH-USD " + down.format(now));
      sendMessage(service, user, msg);
      System.exit(0);
    }
}
