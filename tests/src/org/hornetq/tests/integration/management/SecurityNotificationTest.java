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


package org.hornetq.tests.integration.management;

import static org.hornetq.core.client.management.impl.ManagementHelper.HDR_NOTIFICATION_TYPE;
import static org.hornetq.core.config.impl.ConfigurationImpl.DEFAULT_MANAGEMENT_NOTIFICATION_ADDRESS;
import static org.hornetq.core.management.NotificationType.SECURITY_AUTHENTICATION_VIOLATION;
import static org.hornetq.core.management.NotificationType.SECURITY_PERMISSION_VIOLATION;
import static org.hornetq.tests.util.RandomUtil.randomSimpleString;
import static org.hornetq.tests.util.RandomUtil.randomString;

import java.util.HashSet;
import java.util.Set;

import org.hornetq.core.client.ClientConsumer;
import org.hornetq.core.client.ClientMessage;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.client.management.impl.ManagementHelper;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.TransportConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.exception.HornetQException;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.security.CheckType;
import org.hornetq.core.security.HornetQSecurityManager;
import org.hornetq.core.security.Role;
import org.hornetq.core.server.HornetQ;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.tests.util.UnitTestCase;
import org.hornetq.utils.SimpleString;

/**
 * A SecurityNotificationTest
 *
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 *
 */
public class SecurityNotificationTest extends UnitTestCase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private HornetQServer server;
   private ClientSession adminSession;
   private ClientConsumer notifConsumer;
   private SimpleString notifQueue;

   // Static --------------------------------------------------------
   
   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------
  
   public void testSECURITY_AUTHENTICATION_VIOLATION() throws Exception
   {
      String unknownUser = randomString();
 
      flush(notifConsumer);

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration(InVMConnectorFactory.class.getName()));
      
      try
      {
         sf.createSession(unknownUser, randomString(), false, true, true, false, 1);
         fail("authentication must fail and a notification of security violation must be sent");
      }
      catch (Exception e)
      {
      }
      
      ClientMessage[] notifications = consumeMessages(1, notifConsumer);
      assertEquals(SECURITY_AUTHENTICATION_VIOLATION.toString(), notifications[0].getProperty(HDR_NOTIFICATION_TYPE).toString());
      assertEquals(unknownUser, notifications[0].getProperty(ManagementHelper.HDR_USER).toString());
   }

   public void testSECURITY_PERMISSION_VIOLATION() throws Exception
   {
      SimpleString queue = randomSimpleString();
      SimpleString address = randomSimpleString();

      // guest can not create queue
      Role role = new Role("roleCanNotCreateQueue", true, true, false, true, false, true, true);
      Set<Role> roles = new HashSet<Role>();
      roles.add(role);
      server.getSecurityRepository().addMatch(address.toString(), roles);
      HornetQSecurityManager securityManager =  server.getSecurityManager();
      securityManager.addRole("guest", "roleCanNotCreateQueue");
      
      flush(notifConsumer);

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration(InVMConnectorFactory.class.getName()));
      ClientSession guestSession = sf.createSession("guest", "guest", false, true, true, false, 1);

      try
      {
         guestSession.createQueue(address, queue, true);
         fail("session creation must fail and a notification of security violation must be sent");
      }
      catch (Exception e)
      {
      }
      
      ClientMessage[] notifications = consumeMessages(1, notifConsumer);
      assertEquals(SECURITY_PERMISSION_VIOLATION.toString(), notifications[0].getProperty(HDR_NOTIFICATION_TYPE).toString());
      assertEquals("guest", notifications[0].getProperty(ManagementHelper.HDR_USER).toString());
      assertEquals(address.toString(), notifications[0].getProperty(ManagementHelper.HDR_ADDRESS).toString());
      assertEquals(CheckType.CREATE_DURABLE_QUEUE.toString(), notifications[0].getProperty(ManagementHelper.HDR_CHECK_TYPE).toString());
      
      guestSession.close();
   }
   
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();

      Configuration conf = new ConfigurationImpl();
      conf.setSecurityEnabled(true);
      // the notifications are independent of JMX
      conf.setJMXManagementEnabled(false);
      conf.getAcceptorConfigurations()
          .add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
      server = HornetQ.newMessagingServer(conf, false);
      server.start();

      notifQueue = randomSimpleString();

      HornetQSecurityManager securityManager = server.getSecurityManager();
      securityManager.addUser("admin", "admin");      
      securityManager.addUser("guest", "guest");
      securityManager.setDefaultUser("guest");

      Role role = new Role("notif", true, true, true, true, true, true, true);
      Set<Role> roles = new HashSet<Role>();
      roles.add(role);
      server.getSecurityRepository().addMatch(DEFAULT_MANAGEMENT_NOTIFICATION_ADDRESS.toString(), roles);

      securityManager.addRole("admin", "notif");

      ClientSessionFactory sf = new ClientSessionFactoryImpl(new TransportConfiguration(InVMConnectorFactory.class.getName()));
      adminSession = sf.createSession("admin", "admin", false, true, true, false, 1);
      adminSession.start();
      
      adminSession.createTemporaryQueue(DEFAULT_MANAGEMENT_NOTIFICATION_ADDRESS, notifQueue);

      notifConsumer = adminSession.createConsumer(notifQueue);
   }

   @Override
   protected void tearDown() throws Exception
   {
      notifConsumer.close();
      
      adminSession.deleteQueue(notifQueue);
      adminSession.close();
      
      server.stop();

      super.tearDown();
   }

   // Private -------------------------------------------------------

   
   private static void flush(ClientConsumer notifConsumer) throws HornetQException
   {
      ClientMessage message = null;
      do
      {
         message = notifConsumer.receive(500);
      } while (message != null);
   }

   
   protected static ClientMessage[] consumeMessages(int expected, ClientConsumer consumer) throws Exception
   {
      ClientMessage[] messages = new ClientMessage[expected];
      
      ClientMessage m = null;
      for (int i = 0; i < expected; i++)
      {
         m = consumer.receive(500);
         if (m != null)
         {
            for (SimpleString key : m.getPropertyNames())
            {
               System.out.println(key + "=" + m.getProperty(key));
            }    
         }
         assertNotNull("expected to received " + expected + " messages, got only " + i, m);
         messages[i] = m;
         m.acknowledge();
      }
      m = consumer.receive(500);
      if (m != null)
      {
         for (SimpleString key : m.getPropertyNames())

         {
            System.out.println(key + "=" + m.getProperty(key));
         }
      }    
      assertNull("received one more message than expected (" + expected + ")", m);
      
      return messages;
   }
   
   // Inner classes -------------------------------------------------

}