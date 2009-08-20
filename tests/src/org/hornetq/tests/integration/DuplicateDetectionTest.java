/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.tests.integration;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.hornetq.core.client.ClientConsumer;
import org.hornetq.core.client.ClientMessage;
import org.hornetq.core.client.ClientProducer;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.TransportConfiguration;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.message.impl.MessageImpl;
import org.hornetq.core.server.HornetQ;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.transaction.impl.XidImpl;
import org.hornetq.tests.util.ServiceTestBase;
import org.hornetq.utils.SimpleString;
import org.hornetq.utils.UUIDGenerator;

/**
 * A DuplicateDetectionTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 9 Dec 2008 12:31:48
 *
 *
 */
public class DuplicateDetectionTest extends ServiceTestBase
{
   private static final Logger log = Logger.getLogger(DuplicateDetectionTest.class);

   private HornetQServer messagingService;

   private final SimpleString propKey = new SimpleString("propkey");

   private final int cacheSize = 10;

   public void testSimpleDuplicateDetecion() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      producer.send(message);
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(0, message2.getProperty(propKey));

      message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(1, message2.getProperty(propKey));

      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receive(250);
      assertNull(message2);

      message = createMessage(session, 3);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receive(250);
      assertNull(message2);

      // Now try with a different id

      message = createMessage(session, 4);
      SimpleString dupID2 = new SimpleString("hijklmnop");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(4, message2.getProperty(propKey));

      message = createMessage(session, 5);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertNull(message2);

      message = createMessage(session, 6);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receive(250);
      assertNull(message2);

      session.close();

      sf.close();
   }
   
   public void testSimpleDuplicateDetectionWithString() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      producer.send(message);
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(0, message2.getProperty(propKey));

      message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putStringProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID);
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(1, message2.getProperty(propKey));

      message = createMessage(session, 2);
      message.putStringProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID);
      producer.send(message);
      message2 = consumer.receive(250);
      assertNull(message2);

      message = createMessage(session, 3);
      message.putStringProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID);
      producer.send(message);
      message2 = consumer.receive(250);
      assertNull(message2);

      // Now try with a different id

      message = createMessage(session, 4);
      SimpleString dupID2 = new SimpleString("hijklmnop");
      message.putStringProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2);
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(4, message2.getProperty(propKey));

      message = createMessage(session, 5);
      message.putStringProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2);
      producer.send(message);
      message2 = consumer.receive(1000);
      assertNull(message2);

      message = createMessage(session, 6);
      message.putStringProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID);
      producer.send(message);
      message2 = consumer.receive(250);
      assertNull(message2);

      session.close();

      sf.close();
   }

   public void testCacheSize() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName1 = new SimpleString("DuplicateDetectionTestQueue1");

      final SimpleString queueName2 = new SimpleString("DuplicateDetectionTestQueue2");

      final SimpleString queueName3 = new SimpleString("DuplicateDetectionTestQueue3");

      session.createQueue(queueName1, queueName1, null, false);

      session.createQueue(queueName2, queueName2, null, false);

      session.createQueue(queueName3, queueName3, null, false);

      ClientProducer producer1 = session.createProducer(queueName1);
      ClientConsumer consumer1 = session.createConsumer(queueName1);

      ClientProducer producer2 = session.createProducer(queueName2);
      ClientConsumer consumer2 = session.createConsumer(queueName2);

      ClientProducer producer3 = session.createProducer(queueName3);
      ClientConsumer consumer3 = session.createConsumer(queueName3);

      for (int i = 0; i < cacheSize; i++)
      {
         SimpleString dupID = new SimpleString("dupID" + i);

         ClientMessage message = createMessage(session, i);

         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      for (int i = 0; i < cacheSize; i++)
      {
         ClientMessage message = consumer1.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getProperty(propKey));
         message = consumer2.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getProperty(propKey));
         message = consumer3.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getProperty(propKey));
      }

      log.info("Now sending more");
      for (int i = 0; i < cacheSize; i++)
      {
         SimpleString dupID = new SimpleString("dupID" + i);

         ClientMessage message = createMessage(session, i);

         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      ClientMessage message = consumer1.receive(100);
      assertNull(message);
      message = consumer2.receive(100);
      assertNull(message);
      message = consumer3.receive(100);
      assertNull(message);

      for (int i = 0; i < cacheSize; i++)
      {
         SimpleString dupID = new SimpleString("dupID2-" + i);

         message = createMessage(session, i);

         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      for (int i = 0; i < cacheSize; i++)
      {
         message = consumer1.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getProperty(propKey));
         message = consumer2.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getProperty(propKey));
         message = consumer3.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getProperty(propKey));
      }

      for (int i = 0; i < cacheSize; i++)
      {
         SimpleString dupID = new SimpleString("dupID2-" + i);

         message = createMessage(session, i);

         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      message = consumer1.receive(100);
      assertNull(message);
      message = consumer2.receive(100);
      assertNull(message);
      message = consumer3.receive(100);
      assertNull(message);

      // Should be able to send the first lot again now - since the second lot pushed the
      // first lot out of the cache
      for (int i = 0; i < cacheSize; i++)
      {
         SimpleString dupID = new SimpleString("dupID" + i);

         message = createMessage(session, i);

         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());

         producer1.send(message);
         producer2.send(message);
         producer3.send(message);
      }

      for (int i = 0; i < cacheSize; i++)
      {
         message = consumer1.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getProperty(propKey));
         message = consumer2.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getProperty(propKey));
         message = consumer3.receive(1000);
         assertNotNull(message);
         assertEquals(i, message.getProperty(propKey));
      }

      session.close();

      sf.close();
   }

   public void testTransactedDuplicateDetection1() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.close();

      session = sf.createSession(false, false, false);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should be able to resend it and not get rejected since transaction didn't commit

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.commit();

      message = consumer.receive(250);
      assertEquals(1, message.getProperty(propKey));

      message = consumer.receive(250);
      assertNull(message);

      session.close();

      sf.close();
   }

   public void testTransactedDuplicateDetection2() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.rollback();

      // Should be able to resend it and not get rejected since transaction didn't commit

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);

      session.commit();

      message = consumer.receive(250);
      assertEquals(1, message.getProperty(propKey));

      message = consumer.receive(250);
      assertNull(message);

      session.close();

      sf.close();
   }

   public void testTransactedDuplicateDetection3() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID1 = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID1.getData());
      producer.send(message);

      message = createMessage(session, 1);
      SimpleString dupID2 = new SimpleString("hijklmno");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      session.commit();

      // These next two should get rejected

      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID1.getData());
      producer.send(message);

      message = createMessage(session, 3);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);

      session.commit();

      message = consumer.receive(250);
      assertEquals(0, message.getProperty(propKey));

      message = consumer.receive(250);
      assertEquals(1, message.getProperty(propKey));

      message = consumer.receive(250);
      assertNull(message);

      session.close();

      sf.close();
   }
   
   /*
    * Entire transaction should be rejected on duplicate detection
    * Even if not all entries have dupl id header
    */
   public void testEntireTransactionRejected() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
            
      session.commit();

      session.close();

      session = sf.createSession(false, false, false);

      session.start();

      producer = session.createProducer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      
      message = createMessage(session, 2);
      producer.send(message);
      
      message = createMessage(session, 3);
      producer.send(message);
      
      message = createMessage(session, 4);
      producer.send(message);
      
      session.commit();
      
      ClientConsumer consumer = session.createConsumer(queueName);

      message = consumer.receive(250);
      assertEquals(0, message.getProperty(propKey));

      message = consumer.receive(250);
      assertNull(message);

      session.close();

      sf.close();
   }
   
   public void testXADuplicateDetection1() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(true, false, false);
      
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);
      
      session.start();
      
      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      
      session.end(xid, XAResource.TMSUCCESS);

      session.close();
      
      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);
      
      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should be able to resend it and not get rejected since transaction didn't commit

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      
      session.end(xid2, XAResource.TMSUCCESS);
      
      session.prepare(xid2);

      session.commit(xid2, false);
      
      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      message = consumer.receive(250);
      assertEquals(1, message.getProperty(propKey));

      message = consumer.receive(250);
      assertNull(message);
      
      log.info("ending session");
      session.end(xid3, XAResource.TMSUCCESS);
      
      log.info("preparing session");
      session.prepare(xid3);

      log.info("committing session");
      session.commit(xid3, false);

      session.close();

      sf.close();
   }
   
   public void testXADuplicateDetection2() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(true, false, false);
      
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);
      
      session.start();
      
      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      
      session.end(xid, XAResource.TMSUCCESS);
      
      session.rollback(xid);

      session.close();
      
      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);
      
      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should be able to resend it and not get rejected since transaction didn't commit

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      
      session.end(xid2, XAResource.TMSUCCESS);
      
      session.prepare(xid2);

      session.commit(xid2, false);
      
      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      message = consumer.receive(250);
      assertEquals(1, message.getProperty(propKey));

      message = consumer.receive(250);
      assertNull(message);
      
      log.info("ending session");
      session.end(xid3, XAResource.TMSUCCESS);
      
      log.info("preparing session");
      session.prepare(xid3);

      log.info("committing session");
      session.commit(xid3, false);

      session.close();

      sf.close();
   }
   
   public void testXADuplicateDetection3() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(true, false, false);
      
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);
      
      session.start();
      
      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      
      session.end(xid, XAResource.TMSUCCESS);
      
      session.prepare(xid);

      session.close();
      
      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);
      
      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should NOT be able to resend it 

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      
      session.end(xid2, XAResource.TMSUCCESS);
      
      session.prepare(xid2);

      session.commit(xid2, false);
      
      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      message = consumer.receive(250);

      message = consumer.receive(250);
      assertNull(message);
      
      log.info("ending session");
      session.end(xid3, XAResource.TMSUCCESS);
      
      log.info("preparing session");
      session.prepare(xid3);

      log.info("committing session");
      session.commit(xid3, false);

      session.close();

      sf.close();
   }
   
   public void testXADuplicateDetection4() throws Exception
   {
      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(true, false, false);
      
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);
      
      session.start();
      
      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientMessage message = createMessage(session, 0);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      
      session.end(xid, XAResource.TMSUCCESS);
      
      session.prepare(xid);
      
      session.commit(xid, false);

      session.close();
      
      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session = sf.createSession(true, false, false);
      
      session.start(xid2, XAResource.TMNOFLAGS);

      session.start();

      producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      // Should NOT be able to resend it 

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      
      session.end(xid2, XAResource.TMSUCCESS);
      
      session.prepare(xid2);

      session.commit(xid2, false);
      
      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);

      message = consumer.receive(250);

      message = consumer.receive(250);
      assertNull(message);
      
      log.info("ending session");
      session.end(xid3, XAResource.TMSUCCESS);
      
      log.info("preparing session");
      session.prepare(xid3);

      log.info("committing session");
      session.commit(xid3, false);

      session.close();

      sf.close();
   }

   private ClientMessage createMessage(final ClientSession session, final int i)
   {
      ClientMessage message = session.createClientMessage(false);

      message.putIntProperty(propKey, i);

      return message;
   }

   public void testDuplicateCachePersisted() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      conf.setIDCacheSize(cacheSize);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(1, message2.getProperty(propKey));
      
      message = createMessage(session, 2);
      SimpleString dupID2 = new SimpleString("hijklmnopqr");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(2, message2.getProperty(propKey));
      
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(false, true, true);

      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receive(200);
      assertNull(message2);
      
      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(200);
      assertNull(message2);

      session.close();

      sf.close();
      
      messagingService2.stop();
   }
   
   public void testDuplicateCachePersisted2() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      final int theCacheSize = 5;
      
      conf.setIDCacheSize(theCacheSize);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);
      
      for (int i = 0; i < theCacheSize; i++)
      {
         ClientMessage message = createMessage(session, i);
         SimpleString dupID = new SimpleString("abcdefg" + i);
         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         ClientMessage message2 = consumer.receive(1000);
         assertEquals(i, message2.getProperty(propKey));
      }
      
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(false, true, true);

      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      for (int i = 0; i < theCacheSize; i++)
      {
         ClientMessage message = createMessage(session, i);
         SimpleString dupID = new SimpleString("abcdefg" + i);
         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         ClientMessage message2 = consumer.receive(100);
         assertNull(message2);
      }

      session.close();

      sf.close();
      
      messagingService2.stop();
   }
   
   public void testDuplicateCachePersistedRestartWithSmallerCache() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      final int initialCacheSize = 10;
      final int subsequentCacheSize = 5;
      
      conf.setIDCacheSize(initialCacheSize);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);
      
      for (int i = 0; i < initialCacheSize; i++)
      {
         ClientMessage message = createMessage(session, i);
         SimpleString dupID = new SimpleString("abcdefg" + i);
         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         ClientMessage message2 = consumer.receive(1000);
         assertEquals(i, message2.getProperty(propKey));
      }
      
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      conf.setIDCacheSize(subsequentCacheSize);
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(false, true, true);

      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      for (int i = 0; i < initialCacheSize; i++)
      {
         ClientMessage message = createMessage(session, i);
         SimpleString dupID = new SimpleString("abcdefg" + i);
         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         if (i >= subsequentCacheSize)
         {
            //Message should get through
            ClientMessage message2 = consumer.receive(1000);
            assertEquals(i, message2.getProperty(propKey));
         }
         else
         {
            ClientMessage message2 = consumer.receive(100);
            assertNull(message2);
         }
      }

      session.close();

      sf.close();
      
      messagingService2.stop();           
   }
   
   public void testDuplicateCachePersistedRestartWithSmallerCacheEnsureDeleted() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      final int initialCacheSize = 10;
      final int subsequentCacheSize = 5;
      
      conf.setIDCacheSize(initialCacheSize);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);
      
      for (int i = 0; i < initialCacheSize; i++)
      {
         ClientMessage message = createMessage(session, i);
         SimpleString dupID = new SimpleString("abcdefg" + i);
         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         ClientMessage message2 = consumer.receive(1000);
         assertEquals(i, message2.getProperty(propKey));
      }
      
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      conf.setIDCacheSize(subsequentCacheSize);
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();
      
      //Now stop and set back to original cache size and restart
      
      messagingService2.stop();
      
      conf.setIDCacheSize(initialCacheSize);
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();
      

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(false, true, true);

      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      for (int i = 0; i < initialCacheSize; i++)
      {
         ClientMessage message = createMessage(session, i);
         SimpleString dupID = new SimpleString("abcdefg" + i);
         message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
         producer.send(message);
         if (i >= subsequentCacheSize)
         {
            //Message should get through
            ClientMessage message2 = consumer.receive(1000);
            assertEquals(i, message2.getProperty(propKey));
         }
         else
         {
            ClientMessage message2 = consumer.receive(100);
            assertNull(message2);
         }
      }

      session.close();

      sf.close();
      
      messagingService2.stop();           
   }
   
   public void testNoPersist() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      conf.setIDCacheSize(cacheSize);
      
      conf.setPersistIDCache(false);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, true, true);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(1, message2.getProperty(propKey));
      
      message = createMessage(session, 2);
      SimpleString dupID2 = new SimpleString("hijklmnopqr");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(1000);
      assertEquals(2, message2.getProperty(propKey));
      
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(false, true, true);

      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      message2 = consumer.receive(200);
      assertEquals(1, message2.getProperty(propKey));
      
      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      message2 = consumer.receive(200);
      assertEquals(2, message2.getProperty(propKey));

      session.close();

      sf.close();
      
      messagingService2.stop();
   }
   
   public void testNoPersistTransactional() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      conf.setIDCacheSize(cacheSize);
      
      conf.setPersistIDCache(false);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      session.commit();
      ClientMessage message2 = consumer.receive(1000);
      assertEquals(1, message2.getProperty(propKey));
      
      message = createMessage(session, 2);
      SimpleString dupID2 = new SimpleString("hijklmnopqr");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(1000);
      assertEquals(2, message2.getProperty(propKey));
      
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(false, false, false);

      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(200);
      assertEquals(1, message2.getProperty(propKey));
      
      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(200);
      assertEquals(2, message2.getProperty(propKey));

      session.close();

      sf.close();
      
      messagingService2.stop();
   }
   
   public void testPersistTransactional() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      conf.setIDCacheSize(cacheSize);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(false, false, false);

      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      session.commit();
      ClientMessage message2 = consumer.receive(1000);
      message2.acknowledge();
      session.commit();
      assertEquals(1, message2.getProperty(propKey));
      
      message = createMessage(session, 2);
      SimpleString dupID2 = new SimpleString("hijklmnopqr");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(1000);
      message2.acknowledge();
      session.commit();
      assertEquals(2, message2.getProperty(propKey));
      
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(false, false, false);

      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(200);
      assertNull(message2);
      
      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      session.commit();
      message2 = consumer.receive(200);
      assertNull(message2);

      session.close();

      sf.close();
      
      messagingService2.stop();
   }
   
   public void testNoPersistXA1() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      conf.setIDCacheSize(cacheSize);
      
      conf.setPersistIDCache(false);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(true, false, false);
      
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);
 
      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
                
      message = createMessage(session, 2);
      SimpleString dupID2 = new SimpleString("hijklmnopqr");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      
      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      session.commit(xid, false);
             
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(true, false, false);
      
      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid2, XAResource.TMNOFLAGS);
 
      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
            
      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      
      session.end(xid2, XAResource.TMSUCCESS);
      session.prepare(xid2);
      session.commit(xid2, false);
      
      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);
      
      ClientMessage message2 = consumer.receive(200);
      assertEquals(1, message2.getProperty(propKey));
      
      message2 = consumer.receive(200);
      assertEquals(2, message2.getProperty(propKey));

      session.close();

      sf.close();
      
      messagingService2.stop();
   }
   
   public void testNoPersistXA2() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      conf.setIDCacheSize(cacheSize);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(true, false, false);
      
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);
 
      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
                
      message = createMessage(session, 2);
      SimpleString dupID2 = new SimpleString("hijklmnopqr");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      
      session.end(xid, XAResource.TMSUCCESS);
                  
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(true, false, false);
      
      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid2, XAResource.TMNOFLAGS);
 
      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
            
      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      
      session.end(xid2, XAResource.TMSUCCESS);
      session.prepare(xid2);
      session.commit(xid2, false);
      
      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);
      
      ClientMessage message2 = consumer.receive(200);
      assertEquals(1, message2.getProperty(propKey));
      
      message2 = consumer.receive(200);
      assertEquals(2, message2.getProperty(propKey));

      session.close();

      sf.close();
      
      messagingService2.stop();
   }
   
   public void testPersistXA1() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      conf.setIDCacheSize(cacheSize);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(true, false, false);
      
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);
 
      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
                
      message = createMessage(session, 2);
      SimpleString dupID2 = new SimpleString("hijklmnopqr");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      
      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      session.commit(xid, false);
             
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(true, false, false);
      
      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid2, XAResource.TMNOFLAGS);
 
      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
            
      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      
      session.end(xid2, XAResource.TMSUCCESS);
      session.prepare(xid2);
      session.commit(xid2, false);
      
      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);
      
      ClientMessage message2 = consumer.receive(200);
      assertNull(message2);
      
      message2 = consumer.receive(200);
      assertNull(message2);

      session.close();

      sf.close();
      
      messagingService2.stop();
   }
   
   public void testPersistXA2() throws Exception
   {
      messagingService.stop();
      
      Configuration conf = createDefaultConfig();

      conf.setIDCacheSize(cacheSize);
      
      HornetQServer messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      ClientSession session = sf.createSession(true, false, false);
      
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid, XAResource.TMNOFLAGS);
 
      session.start();

      final SimpleString queueName = new SimpleString("DuplicateDetectionTestQueue");

      session.createQueue(queueName, queueName, null, false);

      ClientProducer producer = session.createProducer(queueName);

      ClientConsumer consumer = session.createConsumer(queueName);

      ClientMessage message = createMessage(session, 1);
      SimpleString dupID = new SimpleString("abcdefg");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
                
      message = createMessage(session, 2);
      SimpleString dupID2 = new SimpleString("hijklmnopqr");
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      
      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
             
      session.close();

      sf.close();
      
      messagingService2.stop();
      
      messagingService2 = HornetQ.newMessagingServer(conf);

      messagingService2.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      session = sf.createSession(true, false, false);
      
      Xid xid2 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid2, XAResource.TMNOFLAGS);
 
      session.start();

      session.createQueue(queueName, queueName, null, false);

      producer = session.createProducer(queueName);

      consumer = session.createConsumer(queueName);

      message = createMessage(session, 1);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID.getData());
      producer.send(message);
            
      message = createMessage(session, 2);
      message.putBytesProperty(MessageImpl.HDR_DUPLICATE_DETECTION_ID, dupID2.getData());
      producer.send(message);
      
      session.end(xid2, XAResource.TMSUCCESS);
      session.prepare(xid2);
      session.commit(xid2, false);
      
      Xid xid3 = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      session.start(xid3, XAResource.TMNOFLAGS);
      
      ClientMessage message2 = consumer.receive(200);
      assertNull(message2);
      
      message2 = consumer.receive(200);
      assertNull(message2);

      session.close();

      sf.close();
      
      messagingService2.stop();
   }

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      
      clearData();
      
      Configuration conf = createDefaultConfig();

      conf.setIDCacheSize(cacheSize);

      messagingService = HornetQ.newMessagingServer(conf, false);

      messagingService.start();
   }

   @Override
   protected void tearDown() throws Exception
   {
      if (messagingService.isStarted())
      {
         messagingService.stop();
      }
      
      messagingService = null;
            
      super.tearDown();
   }
}