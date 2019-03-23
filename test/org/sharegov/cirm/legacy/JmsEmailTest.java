package org.sharegov.cirm.legacy;

import java.io.File;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import org.junit.Test;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.test.OpenCirmTestBase;
import mjson.Json;
/**
 * JmsEmailTest
 * 
 * @author Thomas Hilpold
 */
public class JmsEmailTest extends OpenCirmTestBase {

    public static String from = "hilpold@miamidade.gov";
    public static String to = "hilpold@miamidade.gov;ramon.santiago@miamidade.gov";

    @Test
    public void testSendSr() {
        LegacyEmulator le = new LegacyEmulator();
        Json data = Json.object();
        data.set("boid",1945); //DEV DB
        data.set("subject","LE:emailServiceRequestTo");
        data.set("cc",to);
        data.set("comments","Some comment text");
        le.emailServiceRequestTo(data.toString());
    }

    @Test
    public void testSendEmailStringStringStringString() {
        MessageManager mm = MessageManager.get();
        mm.sendEmail(from, to, "sendEmailTest", "testSendEmailStringStringStringString");
    }

    @Test
    public void testEmailAsAttachment() throws MessagingException {
        File report1 = new File("./testData/RecyclingComplaints_Z1_Z2_Z4.xls");
        MessageManager mm = MessageManager.get();
        // Email for Report 1
        String emailText1 = "This is an emailAsAttachment test.";
        mm.emailAsAttachment(from, to, "emailAsAttachment test", emailText1, "text/plain",
                report1.getAbsolutePath());
    }

    @Test
    public void testSendEmailStringStringStringStringStringMultipart() throws MessagingException {
        File report1 = new File("./testData/RecyclingComplaints_Z1_Z2_Z4.xls");
        MessageManager mm = MessageManager.get();
        // Email for Report 1
        String emailText1 = "This is an email test. Multipart";
        Multipart multiPart1 = new MimeMultipart();

        MimeBodyPart messageText1 = new MimeBodyPart();
        messageText1.setContent(emailText1, "text/plain");
        multiPart1.addBodyPart(messageText1);
        // Attach Report File 1
        MimeBodyPart rarAttachment1 = new MimeBodyPart();
        FileDataSource rarFile1 = new FileDataSource(report1);
        rarAttachment1.setDataHandler(new DataHandler(rarFile1));
        rarAttachment1.setFileName(rarFile1.getName());
        multiPart1.addBodyPart(rarAttachment1);

        mm.sendEmail(from, to, to, to, "Attachment test", multiPart1);
    }

}
