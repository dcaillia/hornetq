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

package org.hornetq.tests.integration.cluster.failover;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.hornetq.core.client.ClientConsumer;
import org.hornetq.core.client.ClientMessage;
import org.hornetq.core.client.ClientProducer;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.client.MessageHandler;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.client.impl.ClientSessionFactoryInternal;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.TransportConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.exception.HornetQException;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.impl.invm.InVMRegistry;
import org.hornetq.core.remoting.impl.invm.TransportConstants;
import org.hornetq.core.server.HornetQ;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.jms.client.HornetQBytesMessage;
import org.hornetq.utils.SimpleString;

/**
 * A MultiThreadRandomFailoverStressTest
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 */
public class XAMultiThreadRandomFailoverTest extends MultiThreadFailoverSupport
{
   protected static final SimpleString ADDRESS = new SimpleString("FailoverTestAddress");

   private static final int RECEIVE_TIMEOUT = 30000;

   private final Logger log = Logger.getLogger(getClass());

   protected HornetQServer liveService;

   protected HornetQServer backupService;

   protected Map<String, Object> backupParams = new HashMap<String, Object>();

   protected Map<ClientSession, Xid> xids;

   private int NUM_THREADS = getNumThreads();

   private final int LATCH_WAIT = getLatchWait();

   private final int NUM_SESSIONS = getNumSessions();

   protected void tearDown() throws Exception
   {
      try
      {
         if (backupService != null && backupService.isStarted())
         {
            backupService.stop();
         }
      }
      catch (Throwable ignored)
      {
      }

      try
      {
         if (liveService != null && liveService.isStarted())
         {
            liveService.stop();
         }
      }
      catch (Throwable ignored)
      {
      }

      liveService = null;
      backupService = null;

      super.tearDown();
   }

   protected int getNumSessions()
   {
      return 10;
   }

   protected int getLatchWait()
   {
      return 20000;
   }

   protected int getNumThreads()
   {
      return 10;
   }

   protected int getNumIterations()
   {
      return 2;
   }

   protected boolean shouldFail()
   {
      return true;
   }

   protected ClientSession createTransactionalSession(ClientSessionFactory sf) throws Exception
   {
      ClientSession sess = sf.createSession(true, false, false);
      return sess;
   }

   public void testC() throws Exception
   {
      runTestMultipleThreads(new RunnableT()
      {
         @Override
         public void run(final ClientSessionFactory sf, final int threadNum) throws Exception
         {
            doTestC(sf, threadNum);
         }
      }, NUM_THREADS, false, 3000);
   }

   public void testD() throws Exception
   {
      runTestMultipleThreads(new RunnableT()
      {
         @Override
         public void run(final ClientSessionFactory sf, final int threadNum) throws Exception
         {
            doTestD(sf, threadNum);
         }
      }, NUM_THREADS, false, 3000);
   }

   public void testG() throws Exception
   {
      runTestMultipleThreads(new RunnableT()
      {
         @Override
         public void run(final ClientSessionFactory sf, final int threadNum) throws Exception
         {
            doTestG(sf, threadNum);
         }
      }, NUM_THREADS, false, 3000);
   }

   public void testH() throws Exception
   {
      runTestMultipleThreads(new RunnableT()
      {
         @Override
         public void run(final ClientSessionFactory sf, final int threadNum) throws Exception
         {
            doTestH(sf, threadNum);
         }
      }, NUM_THREADS, false, 3000);
   }

   protected void doTestC(final ClientSessionFactory sf, final int threadNum) throws Exception
   {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = NUM_SESSIONS;

      Set<MyHandler> handlers = new HashSet<MyHandler>();

      for (int i = 0; i < numSessions; i++)
      {
         SimpleString subName = new SimpleString(threadNum + "sub" + i);

         ClientSession sessConsume = createTransactionalSession(sf);

         sessConsume.createQueue(ADDRESS, subName, null, false);

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         MyHandler handler = new MyHandler(threadNum, numMessages, sessConsume, consumer);

         handler.setCommitOnComplete(false);

         handler.start();

         handlers.add(handler);
      }

      ClientSession sessSend = createTransactionalSession(sf);

      transactionallySendMessages(threadNum, numMessages, sessSend);

      for (MyHandler handler : handlers)
      {
         boolean ok = handler.latch.await(LATCH_WAIT, TimeUnit.MILLISECONDS);

         if (!ok)
         {
            throw new Exception("Timed out waiting for messages on handler " + System.identityHashCode(handler) +
                                " threadnum " +
                                threadNum);
         }

         if (handler.failure != null)
         {
            throw new Exception("Handler failed: " + handler.failure);
         }
      }

      for (MyHandler handler : handlers)
      {
         handler.setCommitOnComplete(true);
         handler.start();
      }

      for (MyHandler handler : handlers)
      {
         boolean ok = handler.latch.await(LATCH_WAIT, TimeUnit.MILLISECONDS);

         assertTrue(ok);
      }

      sessSend.close();

      for (MyHandler handler : handlers)
      {
         handler.close();
      }

      for (int i = 0; i < numSessions; i++)
      {
         SimpleString subName = new SimpleString(threadNum + "sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      for (MyHandler handler : handlers)
      {
         if (handler.failure != null)
         {
            fail(handler.failure);
         }
      }

      long end = System.currentTimeMillis();

      log.info("duration " + (end - start));
   }

   protected void doTestD(final ClientSessionFactory sf, final int threadNum) throws Exception
   {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 10;

      Set<MyHandler> handlers = new HashSet<MyHandler>();

      for (int i = 0; i < numSessions; i++)
      {
         SimpleString subName = new SimpleString(threadNum + " sub" + i);

         ClientSession sessConsume = createTransactionalSession(sf);

         sessConsume.createQueue(ADDRESS, subName, null, false);

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         MyHandler handler = new MyHandler(threadNum, numMessages, sessConsume, consumer);

         handlers.add(handler);
      }

      ClientSession sessSend = createTransactionalSession(sf);

      transactionallySendMessages(threadNum, numMessages, sessSend);

      for (MyHandler handler : handlers)
      {
         handler.session.start();
      }

      for (MyHandler handler : handlers)
      {
         handler.start();
      }

      for (MyHandler handler : handlers)
      {
         boolean ok = handler.latch.await(LATCH_WAIT, TimeUnit.MILLISECONDS);

         if (!ok)
         {
            throw new Exception("Timed out waiting for messages on handler " + System.identityHashCode(handler) +
                                " threadnum " +
                                threadNum);
         }

         if (handler.failure != null)
         {
            throw new Exception("Handler failed: " + handler.failure);
         }
      }

      Set<MyHandler> newhandlers = new HashSet<MyHandler>();

      for (MyHandler handler : handlers)
      {
         MyHandler newHandler = new MyHandler(threadNum, numMessages, handler.session, handler.consumer);
         newHandler.setCommitOnComplete(true);
         newHandler.start();
         newhandlers.add(newHandler);
      }

      handlers.clear();

      handlers = newhandlers;

      for (MyHandler handler : handlers)
      {
         boolean ok = handler.latch.await(LATCH_WAIT, TimeUnit.MILLISECONDS);

         if (!ok)
         {
            throw new Exception("Timed out waiting for messages on handler " + System.identityHashCode(handler) +
                                " threadnum " +
                                threadNum);
         }

         if (handler.failure != null)
         {
            throw new Exception("Handler failed on rollback: " + handler.failure);
         }
      }

      sessSend.close();

      for (MyHandler handler : handlers)
      {
         handler.close();
      }

      for (int i = 0; i < numSessions; i++)
      {
         SimpleString subName = new SimpleString(threadNum + " sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.info("duration " + (end - start));
   }

   protected void doTestG(final ClientSessionFactory sf, final int threadNum) throws Exception
   {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = getNumSessions();

      Set<MyInfo> myinfos = new HashSet<MyInfo>();

      for (int i = 0; i < numSessions; i++)
      {
         SimpleString subName = new SimpleString(threadNum + "sub" + i);

         ClientSession sessConsume = sf.createSession(true, false, false);

         sessConsume.start();

         sessConsume.createQueue(ADDRESS, subName, null, false);

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         Xid xid = newXID();

         sessConsume.start(xid, XAResource.TMNOFLAGS);

         myinfos.add(new MyInfo(sessConsume, consumer, xid));
      }

      ClientSession sessSend = sf.createSession(true, false, false);

      transactionallySendMessages(threadNum, numMessages, sessSend);
      consumeMessages(myinfos, numMessages, threadNum);

      for (MyInfo info : myinfos)
      {
         info.session.end(info.xid, XAResource.TMSUCCESS);
         info.session.prepare(info.xid);
         info.session.rollback(info.xid);
         info.xid = newXID();
         info.session.start(info.xid, XAResource.TMNOFLAGS);
      }

      consumeMessages(myinfos, numMessages, threadNum);

      for (MyInfo info : myinfos)
      {
         info.session.end(info.xid, XAResource.TMSUCCESS);
         info.session.prepare(info.xid);
         info.session.commit(info.xid, false);
         info.xid = null;
      }

      sessSend.close();
      for (MyInfo info : myinfos)
      {
         info.session.close();
      }

      for (int i = 0; i < numSessions; i++)
      {
         SimpleString subName = new SimpleString(threadNum + "sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.info("duration " + (end - start));
   }

   protected void doTestH(final ClientSessionFactory sf, final int threadNum) throws Exception
   {
      long start = System.currentTimeMillis();

      ClientSession s = sf.createSession(false, false, false);

      final int numMessages = 100;

      final int numSessions = 10;

      Set<MyInfo> myinfos = new HashSet<MyInfo>();

      for (int i = 0; i < numSessions; i++)
      {
         SimpleString subName = new SimpleString(threadNum + "sub" + i);

         ClientSession sessConsume = sf.createSession(true, false, false);

         sessConsume.createQueue(ADDRESS, subName, null, false);

         ClientConsumer consumer = sessConsume.createConsumer(subName);

         Xid xid = newXID();

         sessConsume.start(xid, XAResource.TMNOFLAGS);

         myinfos.add(new MyInfo(sessConsume, consumer, xid));
      }

      ClientSession sessSend = sf.createSession(true, false, false);

      transactionallySendMessages(threadNum, numMessages, sessSend);

      for (MyInfo info : myinfos)
      {
         info.session.start();
      }

      consumeMessages(myinfos, numMessages, threadNum);

      for (MyInfo info : myinfos)
      {
         info.session.end(info.xid, XAResource.TMSUCCESS);
         info.session.prepare(info.xid);
         info.session.rollback(info.xid);
         info.xid = newXID();
         info.session.start(info.xid, XAResource.TMNOFLAGS);
      }

      consumeMessages(myinfos, numMessages, threadNum);

      for (MyInfo info : myinfos)
      {
         info.session.end(info.xid, XAResource.TMSUCCESS);
         info.session.prepare(info.xid);
         info.session.commit(info.xid, false);
         info.xid = null;
      }

      sessSend.close();
      for (MyInfo info : myinfos)
      {
         info.session.close();
      }

      for (int i = 0; i < numSessions; i++)
      {
         SimpleString subName = new SimpleString(threadNum + "sub" + i);

         s.deleteQueue(subName);
      }

      s.close();

      long end = System.currentTimeMillis();

      log.info("duration " + (end - start));
   }

   /**
    * @param threadNum
    * @param numMessages
    * @param sessSend
    * @throws XAException
    * @throws HornetQException
    * @throws Exception
    */
   private void transactionallySendMessages(final int threadNum, final int numMessages, ClientSession sessSend) throws XAException,
                                                                                                               HornetQException,
                                                                                                               Exception
   {
      Xid xid = newXID();
      sessSend.start(xid, XAResource.TMNOFLAGS);

      ClientProducer producer = sessSend.createProducer(ADDRESS);

      sendMessages(sessSend, producer, numMessages, threadNum);

      sessSend.end(xid, XAResource.TMSUCCESS);
      sessSend.rollback(xid);

      xid = newXID();
      sessSend.start(xid, XAResource.TMNOFLAGS);

      sendMessages(sessSend, producer, numMessages, threadNum);

      sessSend.end(xid, XAResource.TMSUSPEND);

      sessSend.start(xid, XAResource.TMRESUME);

      sessSend.end(xid, XAResource.TMSUCCESS);

      sessSend.commit(xid, true);
   }

   private void consumeMessages(final Set<MyInfo> myinfos, final int numMessages, final int threadNum) throws Exception
   {
      // We make sure the messages arrive in the order they were sent from a particular producer
      Map<ClientConsumer, Map<Integer, Integer>> counts = new HashMap<ClientConsumer, Map<Integer, Integer>>();

      for (int i = 0; i < numMessages; i++)
      {
         for (MyInfo myinfo : myinfos)
         {
            Map<Integer, Integer> consumerCounts = counts.get(myinfo);

            if (consumerCounts == null)
            {
               consumerCounts = new HashMap<Integer, Integer>();
               counts.put(myinfo.consumer, consumerCounts);
            }

            ClientMessage msg = myinfo.consumer.receive(RECEIVE_TIMEOUT);

            assertNotNull(msg);

            int tn = (Integer)msg.getProperty(new SimpleString("threadnum"));
            int cnt = (Integer)msg.getProperty(new SimpleString("count"));

            Integer c = consumerCounts.get(tn);
            if (c == null)
            {
               c = new Integer(cnt);
            }

            if (tn == threadNum && cnt != c.intValue())
            {
               throw new Exception("Invalid count, expected " + tn + ": " + c + " got " + cnt);
            }

            c++;

            // Wrap
            if (c == numMessages)
            {
               c = 0;
            }

            consumerCounts.put(tn, c);

            msg.acknowledge();
         }
      }
   }

   @Override
   protected void start() throws Exception
   {
      Configuration backupConf = new ConfigurationImpl();
      backupConf.setSecurityEnabled(false);
      backupParams.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      backupConf.getAcceptorConfigurations()
                .add(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory",
                                                backupParams));
      backupConf.setBackup(true);
      backupService = HornetQ.newMessagingServer(backupConf, false);
      backupService.start();

      Configuration liveConf = new ConfigurationImpl();
      liveConf.setSecurityEnabled(false);
      liveConf.getAcceptorConfigurations()
              .add(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory"));
      Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();
      TransportConfiguration backupTC = new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory",
                                                                   backupParams,
                                                                   "backup-connector");
      connectors.put(backupTC.getName(), backupTC);
      liveConf.setConnectorConfigurations(connectors);
      liveConf.setBackupConnectorName(backupTC.getName());
      liveService = HornetQ.newMessagingServer(liveConf, false);
      liveService.start();
   }

   /* (non-Javadoc)
    * @see org.hornetq.tests.integration.cluster.failover.MultiThreadFailoverSupport#stop()
    */
   @Override
   protected void stop() throws Exception
   {
      backupService.stop();

      liveService.stop();

      backupService = null;

      liveService = null;

      assertEquals(0, InVMRegistry.instance.size());
   }

   @Override
   protected ClientSessionFactoryInternal createSessionFactory()
   {
      final ClientSessionFactoryInternal sf = new ClientSessionFactoryImpl(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"),
                                                                           new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory",
                                                                                                      backupParams));

      sf.setProducerWindowSize(32 * 1024);
      return sf;
   }

   /* (non-Javadoc)
    * @see org.hornetq.tests.integration.cluster.failover.MultiThreadRandomFailoverTestBase#setBody(org.hornetq.core.client.ClientMessage)
    */
   protected void setBody(final ClientMessage message) throws Exception
   {
   }

   /* (non-Javadoc)
    * @see org.hornetq.tests.integration.cluster.failover.MultiThreadRandomFailoverTestBase#checkSize(org.hornetq.core.client.ClientMessage)
    */
   protected boolean checkSize(final ClientMessage message)
   {
      return 0 == message.getBody().writerIndex();
   }

   private void runTestMultipleThreads(final RunnableT runnable,
                                       final int numThreads,
                                       final boolean failOnCreateConnection,
                                       final long failDelay) throws Exception
   {

      runMultipleThreadsFailoverTest(runnable, numThreads, getNumIterations(), failOnCreateConnection, failDelay);
   }

   private void sendMessages(final ClientSession sessSend,
                             final ClientProducer producer,
                             final int numMessages,
                             final int threadNum) throws Exception
   {
      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = sessSend.createClientMessage(HornetQBytesMessage.TYPE,
                                                              false,
                                                              0,
                                                              System.currentTimeMillis(),
                                                              (byte)1);
         message.putIntProperty(new SimpleString("threadnum"), threadNum);
         message.putIntProperty(new SimpleString("count"), i);
         setBody(message);
         producer.send(message);
      }
   }

   private class MyInfo
   {
      final ClientSession session;

      Xid xid;

      final ClientConsumer consumer;

      public MyInfo(final ClientSession session, final ClientConsumer consumer, final Xid xid)
      {
         this.session = session;
         this.consumer = consumer;
         this.xid = xid;
      }
   }

   private class MyHandler implements MessageHandler
   {
      CountDownLatch latch = new CountDownLatch(1);

      private final Map<Integer, Integer> counts = new HashMap<Integer, Integer>();

      volatile String failure;

      final int tn;

      final int numMessages;

      final ClientSession session;

      final ClientConsumer consumer;

      volatile Xid xid;

      volatile boolean done;

      volatile boolean started = false;

      volatile boolean commit = false;

      synchronized void start() throws Exception
      {
         counts.clear();

         done = false;

         failure = null;

         latch = new CountDownLatch(1);

         xid = newXID();
         session.start(xid, XAResource.TMNOFLAGS);
         started = true;
         consumer.setMessageHandler(this);
         session.start();
      }

      synchronized void stop() throws Exception
      {
         session.stop();
         // FIXME: Remove this line when https://jira.jboss.org/jira/browse/JBMESSAGING-1549 is done
         consumer.setMessageHandler(null);
         started = false;
      }

      synchronized void close() throws Exception
      {
         stop();
         session.close();
      }

      private synchronized void rollback()
      {
         try
         {
            stop();
            session.end(xid, XAResource.TMSUCCESS);
            session.prepare(xid);
            session.rollback(xid);
         }
         catch (Exception e)
         {
            this.failure = e.getLocalizedMessage();
         }
      }

      private synchronized void commit()
      {
         try
         {
            stop();

            // Suspend & resume... just exercising the API as part of the test
            session.end(xid, XAResource.TMSUSPEND);
            session.start(xid, XAResource.TMRESUME);

            session.end(xid, XAResource.TMSUCCESS);
            session.prepare(xid);
            session.commit(xid, false);
         }
         catch (Exception e)
         {
            this.failure = e.getLocalizedMessage();
         }
      }

      MyHandler(final int threadNum, final int numMessages, final ClientSession session, final ClientConsumer consumer) throws Exception
      {
         tn = threadNum;

         this.numMessages = numMessages;

         this.session = session;

         this.consumer = consumer;

      }

      public void setCommitOnComplete(boolean commit)
      {
         this.commit = commit;
      }

      public synchronized void onMessage(final ClientMessage message)
      {

         if (!started)
         {
            this.failure = "Received message with session stopped (thread = " + tn + ")";
            log.error(failure);
            return;
         }

         try
         {
            message.acknowledge();
         }
         catch (HornetQException me)
         {
            log.error("Failed to process", me);
         }

         if (done)
         {
            return;
         }

         int threadNum = (Integer)message.getProperty(new SimpleString("threadnum"));
         int cnt = (Integer)message.getProperty(new SimpleString("count"));

         Integer c = counts.get(threadNum);
         if (c == null)
         {
            c = new Integer(cnt);
         }

         if (tn == threadNum && cnt != c.intValue())
         {
            failure = "Invalid count, expected " + threadNum + ":" + c + " got " + cnt;
            log.error(failure);

            latch.countDown();
         }

         if (!checkSize(message))
         {
            failure = "Invalid size on message";
            log.error(failure);
            latch.countDown();
         }

         if (tn == threadNum && c == numMessages - 1)
         {
            done = true;
            if (commit)
            {
               commit();
            }
            else
            {
               rollback();
            }
            latch.countDown();
         }

         c++;
         // Wrap around at numMessages
         if (c == numMessages)
         {
            c = 0;
         }

         counts.put(threadNum, c);

      }
   }

}