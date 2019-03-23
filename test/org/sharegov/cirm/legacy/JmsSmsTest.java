package org.sharegov.cirm.legacy;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.sharegov.cirm.test.OpenCirmTestBase;

/**
 * JmsSmsTest
 * 
 * @author Thomas Hilpold
 */
public class JmsSmsTest extends OpenCirmTestBase {

    @Test
    public void testSendMessages() {
        List<CirmMessage> l = new ArrayList<CirmMessage>();
        for (int i = 0; i < 5; i ++) {
            CirmSmsMessage m = new CirmSmsMessage();
            m.setCreationTime(new Date());
            m.setPhone("9543001632");
            m.setTxt("This is a test sms nr " + i + " form the ICE team. \r\n Please visit www.miamidade.gov");
            l.add(m);
        }
        MessageManager mm = MessageManager.get();
        mm.sendMessages(l);
    }

}
