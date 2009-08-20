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
package org.hornetq.jms.soak.example;

import java.io.Serializable;

/**
 * 
 * Class that holds the parameters used in the performance examples
 * 
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
 */
public class SoakParams implements Serializable
{
   private static final long serialVersionUID = -4336539641012356002L;
   
   private int durationInMinutes = 60;
   private int noOfWarmupMessages;
   private int messageSize = 1024; // in bytes
   private boolean durable = false;
   private boolean isSessionTransacted = false;
   private int batchSize = 5000;
   private boolean drainQueue = true;
   private String connectionFactoryLookup;
   private String destinationLookup;
   private int throttleRate;
   private boolean disableMessageID;
   private boolean disableTimestamp;
   private boolean dupsOK;
   
   public synchronized int getDurationInMinutes()
   {
      return durationInMinutes;
   }
   public synchronized void setDurationInMinutes(int durationInMinutes)
   {
      this.durationInMinutes = durationInMinutes;
   }
   public synchronized int getNoOfWarmupMessages()
   {
      return noOfWarmupMessages;
   }
   public synchronized void setNoOfWarmupMessages(int noOfWarmupMessages)
   {
      this.noOfWarmupMessages = noOfWarmupMessages;
   }
   public synchronized int getMessageSize()
   {
      return messageSize;
   }
   public synchronized void setMessageSize(int messageSize)
   {
      this.messageSize = messageSize;
   }
   public synchronized boolean isDurable()
   {
      return durable;
   }
   public synchronized void setDurable(boolean durable)
   {
      this.durable = durable;
   }
   public synchronized boolean isSessionTransacted()
   {
      return isSessionTransacted;
   }
   public synchronized void setSessionTransacted(boolean isSessionTransacted)
   {
      this.isSessionTransacted = isSessionTransacted;
   }
   public synchronized int getBatchSize()
   {
      return batchSize;
   }
   public synchronized void setBatchSize(int batchSize)
   {
      this.batchSize = batchSize;
   }
   public synchronized boolean isDrainQueue()
   {
      return drainQueue;
   }
   public synchronized void setDrainQueue(boolean drainQueue)
   {
      this.drainQueue = drainQueue;
   }
   public synchronized String getConnectionFactoryLookup()
   {
      return connectionFactoryLookup;
   }
   public synchronized void setConnectionFactoryLookup(String connectionFactoryLookup)
   {
      this.connectionFactoryLookup = connectionFactoryLookup;
   }
   public synchronized String getDestinationLookup()
   {
      return destinationLookup;
   }
   public synchronized void setDestinationLookup(String destinationLookup)
   {
      this.destinationLookup = destinationLookup;
   }
   public synchronized int getThrottleRate()
   {
      return throttleRate;
   }
   public synchronized void setThrottleRate(int throttleRate)
   {
      this.throttleRate = throttleRate;
   }
   public synchronized boolean isDisableMessageID()
   {
      return disableMessageID;
   }
   public synchronized void setDisableMessageID(boolean disableMessageID)
   {
      this.disableMessageID = disableMessageID;
   }
   public synchronized boolean isDisableTimestamp()
   {
      return disableTimestamp;
   }
   public synchronized void setDisableTimestamp(boolean disableTimestamp)
   {
      this.disableTimestamp = disableTimestamp;
   }
   public synchronized boolean isDupsOK()
   {
      return dupsOK;
   }
   public synchronized void setDupsOK(boolean dupsOK)
   {
      this.dupsOK = dupsOK;
   }   
     


}