package com.feiyu.deeplearning.RBM;
/**
 * @author feiyu
 */

import com.feiyu.spark.SparkTwitterStreaming;
import com.feiyu.utils.GlobalVariables;
import com.feiyu.utils.InitializeWCR;
import com.feiyu.websocket.SparkHistogramWebSocketHandler;
import com.feiyu.websocket.SparkWebSocketHandler;

public class DataCollectionModelTrainingTestingSparkStreaming implements java.io.Serializable {
  private static final long serialVersionUID = 7231112414625079948L;

  public void init() throws Exception {

    InitializeWCR initWcr = new InitializeWCR();

    initWcr.getWiseCrowdRecConfigInfo();
    initWcr.twitterInitDyna();
    initWcr.elasticsearchInitial();
    initWcr.coreNLPInitial();
    initWcr.calaisNLPInitial();
    initWcr.getFreebaseInfo();

    initWcr.initializeRBM();

    initWcr.themoviedbOrgInitial();
    initWcr.rabbitmqInit();

    Thread SparkHistogramWebSocketHandlerThread = new Thread () {
      public void run () {
        try {
          SparkHistogramWebSocketHandler.start();
        } catch (Exception e) {
          e.printStackTrace();
        } 
      }
    };
    SparkHistogramWebSocketHandlerThread.start();

    Thread SparkWebSocketHandlerThread = new Thread () {
      public void run () {
        try {
          SparkWebSocketHandler.start();//Open Spark server side websocket
        } catch (Exception e) {
          e.printStackTrace();
        } 
      }
    };
    SparkWebSocketHandlerThread.start();
  }

  public static void main(String[] argv) throws Exception {
    DataCollectionModelTrainingTestingSparkStreaming rbmSpark = new DataCollectionModelTrainingTestingSparkStreaming();
    rbmSpark.init();

    SparkTwitterStreaming sts = new SparkTwitterStreaming();
    sts.sparkInit();
    sts.startSpark("movie");

    GlobalVariables.RBM_DATA_CLC_MDL_TRN_TST.start();
  }
}
