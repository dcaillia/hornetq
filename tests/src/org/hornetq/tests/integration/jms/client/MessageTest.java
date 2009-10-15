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

package org.hornetq.tests.integration.jms.client;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.hornetq.core.logging.Logger;
import org.hornetq.tests.util.JMSTestBase;

/**
 * 
 * A MessageTest
 *
 * @author tim
 *
 *
 */
public class MessageTest extends JMSTestBase
{
   // Constants -----------------------------------------------------
   
   private static final Logger log = Logger.getLogger(MessageTest.class);
   
   private static final long TIMEOUT = 1000;

   private static final String propName1 = "myprop1";
   private static final String propName2 = "myprop2";
   private static final String propName3 = "myprop3";
   
   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testNullProperties() throws Exception
   {
      Connection conn = cf.createConnection();

      Queue queue = createQueue("testQueue");

      try
      {
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sess.createProducer(queue);

         MessageConsumer cons = sess.createConsumer(queue);
         
         conn.start();

         Message msg = sess.createMessage();
                  
         msg.setObjectProperty(propName1, null);
         msg.setStringProperty(propName2, null);
         
         checkProperties(msg);
         
         Message received = sendAndConsumeMessage(msg, prod, cons);
         
         assertNotNull(received);
         
         checkProperties(received);
      }
      finally
      {
         try
         {
            conn.close();
         }
         catch (Throwable igonred)
         {
         }
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   private void checkProperties(final Message message) throws Exception
   {
      assertNull(message.getObjectProperty(propName1));
      assertNull(message.getStringProperty(propName1));
      assertNull(message.getStringProperty(propName2));
      assertNull(message.getObjectProperty(propName2));
      assertNull(message.getStringProperty(propName3));
      assertNull(message.getObjectProperty(propName3));
      
      try
      {
         log.info(message.getIntProperty(propName1));
         fail("Should throw exception");
      }
      catch (NumberFormatException e)
      {
         //Ok
      }
      
      try
      {
         log.info(message.getShortProperty(propName1));
      }
      catch (NumberFormatException e)
      {
         //Ok
      }
      try
      {
         log.info(message.getByteProperty(propName1));
      }
      catch (NumberFormatException e)
      {
         //Ok
      }
      assertEquals(false, message.getBooleanProperty(propName1));
      try
      {
         log.info(message.getLongProperty(propName1));
      }
      catch (NumberFormatException e)
      {
         //Ok
      }
      try
      {
         log.info(message.getFloatProperty(propName1));
      }
      catch (NullPointerException e)
      {
         //Ok
      }
      try
      {
         log.info(message.getDoubleProperty(propName1));
      }
      catch (NullPointerException e)
      {
         //Ok
      }
   }
   
   private Message sendAndConsumeMessage(final Message msg, final MessageProducer prod, final MessageConsumer cons) throws Exception
   {
      prod.send(msg);

      Message received = cons.receive(TIMEOUT);

      return received;
   }

   // Inner classes -------------------------------------------------
}