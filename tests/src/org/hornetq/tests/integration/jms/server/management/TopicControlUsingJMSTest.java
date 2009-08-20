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

package org.hornetq.tests.integration.jms.server.management;

import static org.hornetq.core.config.impl.ConfigurationImpl.DEFAULT_MANAGEMENT_ADDRESS;
import static org.hornetq.tests.util.RandomUtil.randomLong;
import static org.hornetq.tests.util.RandomUtil.randomString;

import javax.jms.Connection;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TopicSubscriber;

import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.TransportConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.management.ResourceNames;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.server.HornetQ;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.jms.HornetQQueue;
import org.hornetq.jms.HornetQTopic;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.hornetq.jms.server.impl.JMSServerManagerImpl;
import org.hornetq.tests.integration.management.ManagementTestBase;

public class TopicControlUsingJMSTest extends ManagementTestBase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private HornetQServer server;

   private JMSServerManagerImpl serverManager;

   private String clientID;

   private String subscriptionName;

   protected HornetQTopic topic;

   protected JMSMessagingProxy proxy;

   private QueueConnection connection;

   private QueueSession session;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testGetAttributes() throws Exception
   {
      assertEquals(topic.getTopicName(), proxy.retrieveAttributeValue("name"));
      assertEquals(topic.getAddress(), proxy.retrieveAttributeValue("address"));
      assertEquals(topic.isTemporary(), proxy.retrieveAttributeValue("temporary"));
      assertEquals(topic.getName(), proxy.retrieveAttributeValue("JNDIBinding"));
   }

   public void testGetXXXSubscriptionsCount() throws Exception
   {
      Connection connection_1 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());

      // 1 non-durable subscriber, 2 durable subscribers
      JMSUtil.createConsumer(connection_1, topic);

      Connection connection_2 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createDurableSubscriber(connection_2, topic, clientID, subscriptionName);
      Connection connection_3 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createDurableSubscriber(connection_3, topic, clientID, subscriptionName + "2");

      assertEquals(3, proxy.retrieveAttributeValue("subscriptionCount"));
      assertEquals(1, proxy.retrieveAttributeValue("nonDurableSubscriptionCount"));
      assertEquals(2, proxy.retrieveAttributeValue("durableSubscriptionCount"));

      connection_1.close();
      connection_2.close();
      connection_3.close();
   }

   public void testGetXXXMessagesCount() throws Exception
   {
      // 1 non-durable subscriber, 2 durable subscribers
      Connection connection_1 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createConsumer(connection_1, topic);
      Connection connection_2 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createDurableSubscriber(connection_2, topic, clientID, subscriptionName);
      Connection connection_3 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createDurableSubscriber(connection_3, topic, clientID, subscriptionName + "2");

      assertEquals(0, proxy.retrieveAttributeValue("messageCount"));
      assertEquals(0, proxy.retrieveAttributeValue("nonDurableMessageCount"));
      assertEquals(0, proxy.retrieveAttributeValue("durableMessageCount"));

      JMSUtil.sendMessages(topic, 2);

      assertEquals(3 * 2, proxy.retrieveAttributeValue("messageCount"));
      assertEquals(1 * 2, proxy.retrieveAttributeValue("nonDurableMessageCount"));
      assertEquals(2 * 2, proxy.retrieveAttributeValue("durableMessageCount"));

      connection_1.close();
      connection_2.close();
      connection_3.close();
   }

   public void testListXXXSubscriptionsCount() throws Exception
   {
      // 1 non-durable subscriber, 2 durable subscribers
      Connection connection_1 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createConsumer(connection_1, topic);
      Connection connection_2 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createDurableSubscriber(connection_2, topic, clientID, subscriptionName);
      Connection connection_3 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createDurableSubscriber(connection_3, topic, clientID, subscriptionName + "2");

      assertEquals(3, ((Object[])proxy.invokeOperation("listAllSubscriptions")).length);
      assertEquals(1, ((Object[])proxy.invokeOperation("listNonDurableSubscriptions")).length);
      assertEquals(2, ((Object[])proxy.invokeOperation("listDurableSubscriptions")).length);

      connection_1.close();
      connection_2.close();
      connection_3.close();
   }

   public void testCountMessagesForSubscription() throws Exception
   {
      String key = "key";
      long matchingValue = randomLong();
      long unmatchingValue = matchingValue + 1;

      Connection connection = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createDurableSubscriber(connection, topic, clientID, subscriptionName);

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      JMSUtil.sendMessageWithProperty(session, topic, key, matchingValue);
      JMSUtil.sendMessageWithProperty(session, topic, key, unmatchingValue);
      JMSUtil.sendMessageWithProperty(session, topic, key, matchingValue);

      assertEquals(3, proxy.retrieveAttributeValue("messageCount"));

      assertEquals(2, proxy.invokeOperation("countMessagesForSubscription", clientID, subscriptionName, key + " =" +
                                                                                                        matchingValue));
      assertEquals(1,
                   proxy.invokeOperation("countMessagesForSubscription", clientID, subscriptionName, key + " =" +
                                                                                                     unmatchingValue));

      connection.close();
   }

   public void testCountMessagesForUnknownSubscription() throws Exception
   {
      String unknownSubscription = randomString();

      try
      {
         proxy.invokeOperation("countMessagesForSubscription", clientID, unknownSubscription, null);
         fail();
      }
      catch (Exception e)
      {
      }
   }

   public void testCountMessagesForUnknownClientID() throws Exception
   {
      String unknownClientID = randomString();

      try
      {
         proxy.invokeOperation("countMessagesForSubscription", unknownClientID, subscriptionName, null);
         fail();
      }
      catch (Exception e)
      {
      }
   }

   public void testDropDurableSubscriptionWithExistingSubscription() throws Exception
   {
      Connection connection = JMSUtil.createConnection(InVMConnectorFactory.class.getName());

      JMSUtil.createDurableSubscriber(connection, topic, clientID, subscriptionName);

      assertEquals(1, proxy.retrieveAttributeValue("durableSubscriptionCount"));

      connection.close();

      proxy.invokeOperation("dropDurableSubscription", clientID, subscriptionName);

      assertEquals(0, proxy.retrieveAttributeValue("durableSubscriptionCount"));
   }

   public void testDropDurableSubscriptionWithUnknownSubscription() throws Exception
   {
      Connection connection = JMSUtil.createConnection(InVMConnectorFactory.class.getName());

      JMSUtil.createDurableSubscriber(connection, topic, clientID, subscriptionName);

      assertEquals(1, proxy.retrieveAttributeValue("durableSubscriptionCount"));

      try
      {
         proxy.invokeOperation("dropDurableSubscription", clientID, "this subscription does not exist");
         fail("should throw an exception");
      }
      catch (Exception e)
      {

      }

      assertEquals(1, proxy.retrieveAttributeValue("durableSubscriptionCount"));

      connection.close();
   }

   public void testDropAllSubscriptions() throws Exception
   {
      Connection connection_1 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      TopicSubscriber durableSubscriber_1 = JMSUtil.createDurableSubscriber(connection_1,
                                                                            topic,
                                                                            clientID,
                                                                            subscriptionName);
      Connection connection_2 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      TopicSubscriber durableSubscriber_2 = JMSUtil.createDurableSubscriber(connection_2,
                                                                            topic,
                                                                            clientID,
                                                                            subscriptionName + "2");

      assertEquals(2, proxy.retrieveAttributeValue("subscriptionCount"));

      durableSubscriber_1.close();
      durableSubscriber_2.close();

      assertEquals(2, proxy.retrieveAttributeValue("subscriptionCount"));
      proxy.invokeOperation("dropAllSubscriptions");

      assertEquals(0, proxy.retrieveAttributeValue("subscriptionCount"));

      connection_1.close();
      connection_2.close();
   }

   public void testRemoveAllMessages() throws Exception
   {
      Connection connection_1 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createDurableSubscriber(connection_1, topic, clientID, subscriptionName);
      Connection connection_2 = JMSUtil.createConnection(InVMConnectorFactory.class.getName());
      JMSUtil.createDurableSubscriber(connection_2, topic, clientID, subscriptionName + "2");

      JMSUtil.sendMessages(topic, 3);

      assertEquals(3 * 2, proxy.retrieveAttributeValue("messageCount"));

      int removedCount = (Integer)proxy.invokeOperation("removeMessages", "");
      assertEquals(3 * 2, removedCount);
      assertEquals(0, proxy.retrieveAttributeValue("messageCount"));

      connection_1.close();
      connection_2.close();
   }

   public void testListMessagesForSubscription() throws Exception
   {
      Connection connection = JMSUtil.createConnection(InVMConnectorFactory.class.getName());

      JMSUtil.createDurableSubscriber(connection, topic, clientID, subscriptionName);

      JMSUtil.sendMessages(topic, 3);

      Object[] data = (Object[])proxy.invokeOperation("listMessagesForSubscription",
                                                      HornetQTopic.createQueueNameForDurableSubscription(clientID,
                                                                                                       subscriptionName));
      assertEquals(3, data.length);
      
      connection.close();
   }

   public void testListMessagesForSubscriptionWithUnknownClientID() throws Exception
   {
      String unknownClientID = randomString();

      try
      {
         proxy.invokeOperation("listMessagesForSubscription",
                               HornetQTopic.createQueueNameForDurableSubscription(unknownClientID, subscriptionName));
         fail();
      }
      catch (Exception e)
      {
      }
   }

   public void testListMessagesForSubscriptionWithUnknownSubscription() throws Exception
   {
      String unknownSubscription = randomString();

      try
      {
         proxy.invokeOperation("listMessagesForSubscription",
                               HornetQTopic.createQueueNameForDurableSubscription(clientID, unknownSubscription));
         fail();
      }
      catch (Exception e)
      {
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();

      Configuration conf = new ConfigurationImpl();
      conf.setSecurityEnabled(false);
      conf.setJMXManagementEnabled(true);
      conf.getAcceptorConfigurations()
          .add(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory"));
      server = HornetQ.newMessagingServer(conf, mbeanServer, false);
      server.start();

      serverManager = new JMSServerManagerImpl(server);
      serverManager.start();
      serverManager.setContext(new NullInitialContext());
      serverManager.activated();

      clientID = randomString();
      subscriptionName = randomString();

      String topicName = randomString();
      serverManager.createTopic(topicName, topicName);
      topic = new HornetQTopic(topicName);

      HornetQConnectionFactory cf = new HornetQConnectionFactory(new TransportConfiguration(InVMConnectorFactory.class.getName()));
      connection = cf.createQueueConnection();
      session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
      connection.start();

      HornetQQueue managementQueue = new HornetQQueue(DEFAULT_MANAGEMENT_ADDRESS.toString(),
                                                  DEFAULT_MANAGEMENT_ADDRESS.toString());
      proxy = new JMSMessagingProxy(session, managementQueue, ResourceNames.JMS_TOPIC + topic.getTopicName());
   }

   @Override
   protected void tearDown() throws Exception
   {
      
      session.close();
      
      connection.close();

      serverManager.stop();
      
      server.stop();
      
      serverManager = null;
      
      server = null;
      
      session = null;
      
      connection = null;
      
      proxy = null;

      super.tearDown();
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}