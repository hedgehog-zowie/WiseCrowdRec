package com.feiyu.deeplearning.RBM;

/**
 * @author Fei Yu (@faustineinsun)
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.json.simple.parser.ParseException;

import com.feiyu.classes.RBMClientWeightMatixForPredict;
import com.feiyu.classes.RBMDataQueueElementInfo;
import com.feiyu.classes.RBMMovieInfo;
import com.feiyu.classes.RBMUserInfo;
import com.feiyu.classes.Tuple;
import com.feiyu.utils.GlobalVariables;
import com.feiyu.utils.InitializeWCR;
import com.omertron.themoviedbapi.MovieDbException;

public class ThreadRBMTrainingKMins  implements Runnable {
  private static Logger log = Logger.getLogger(ThreadRBMTrainingKMins.class.getName());
  private String threadName;
  private RBMDataQueueElementInfo currentData;
  private int numMovies; // move list size changes in each RBM Model 
  private boolean successfullyTrainedThisRBM;
  private String RMSEfileName;
  private BufferedWriter bw;

  public ThreadRBMTrainingKMins(String threadName , RBMDataQueueElementInfo currentData) {
    this.threadName = threadName;
    this.currentData = new RBMDataQueueElementInfo(
      currentData.getKthRBM(),
      new HashMap<String, RBMMovieInfo>(currentData.getMovieHashMap()),
      new HashMap<String, RBMUserInfo>(currentData.getUserHashMapTrain()),
      new HashMap<String, RBMUserInfo>(currentData.getUserHashMapTest()),
      new ArrayList<String>(currentData.getMovieNameWithIdx()));

    this.numMovies = currentData.getMovieHashMap().size();
    this.successfullyTrainedThisRBM = false;
    GlobalVariables.RBM_CLIENT_RATED_MOVIES_CUR_RBM = new HashMap<Integer, Integer>();
    log.debug("Creating " +  this.threadName + " at " +System.currentTimeMillis());
  }

  public void run() {
    try {
      this.printTrainingTestingData();

      // Train RBM 
      this.trainRBM();

      Thread.sleep(1);
    } catch (InterruptedException e) {
      log.debug(this.threadName+" is interrupted at " + System.currentTimeMillis());
    } catch (IOException | ParseException e) {
      e.printStackTrace();
    }
  }

  private void trainRBM() throws ParseException, IOException {
    this.createFileForRMSE();

    // do not show Data & trained Weight Matrix to Client 
    // when Epoch for RBM is from 1 to GlobalVariables.RBM_NUM_EPOCHS-1
    if (GlobalVariables.RBM_DRAW_CHART) {
      for (int i=1; i<GlobalVariables.RBM_NUM_EPOCHS; i++) {
        this.trainRBMWithCertainEpoch(i, false);
      }
    } 

    // show Data & trained Weight Matrix to Client
    // when Epoch for RBM is GlobalVariables.RBM_NUM_EPOCHS
    this.trainRBMWithCertainEpoch(GlobalVariables.RBM_NUM_EPOCHS, true);

    bw.close();
    log.debug("Saved rmse-by-epoch to "+GlobalVariables.LOG_DIR+RMSEfileName+".txt!!");
  }

  private void createFileForRMSE() throws IOException {
    // create a file for collecting the RMSE of Epochs from 1 to numEpochs
    this.RMSEfileName = "RMSEByEpochs_TrainedRBMModel+"+this.currentData.getKthRBM();
    File file = new File(GlobalVariables.LOG_DIR+RMSEfileName+".txt");
    if (!file.exists()) {
      file.createNewFile();
    }
    FileWriter fw = new FileWriter(file.getAbsoluteFile());
    bw = new BufferedWriter(fw);
  }

  private void trainRBMWithCertainEpoch(int epochs, boolean showDataNWeightMatrix2Client) throws IOException, ParseException {			
    RestrictedBoltzmannMachinesWithSoftmax rbmSoftmax = new RestrictedBoltzmannMachinesWithSoftmax(
      this.numMovies, GlobalVariables.RBM_SIZE_SOFTMAX, GlobalVariables.RBM_SIZE_HIDDEN_UNITS, 
      GlobalVariables.RBM_LEARNING_RATE, epochs, bw
        );
    // Train RBM
    this.trainOrTestRBM(rbmSoftmax, true);

    if (showDataNWeightMatrix2Client) {
      // 1) updata the Client Weight Matrix for prediction
      this.updateClientWeightMatixForPrediction(rbmSoftmax);
      // Another thread: 
      // 2) show movies & genres to client 
      //this.showData2Client(); //@@@@@
    }

    // Test RBM
    this.trainOrTestRBM(rbmSoftmax, false);

    // Show test results in RMSE
    // rbmSoftmax.getTrainedWeightMatrix_RBM();
    rbmSoftmax.getRMSEOfRBMModel();
  }

  private void trainOrTestRBM(RestrictedBoltzmannMachinesWithSoftmax rbmSoftmax, boolean isForTrain) throws IOException {
    Iterator<Entry<String, RBMUserInfo>> itUser;
    if (isForTrain) {
      this.successfullyTrainedThisRBM = false;
      itUser = this.currentData.getUserHashMapTrain().entrySet().iterator();
      log.debug("~~~~~~~~~~~Train RBM "+itUser.hasNext());
    } else {
      itUser = this.currentData.getUserHashMapTest().entrySet().iterator();
      log.debug("~~~~~~~~~~~Test RBM "+itUser.hasNext());
    }
    while (itUser.hasNext()) {
      Map.Entry<String, RBMUserInfo> pairs = (Map.Entry<String, RBMUserInfo>)itUser.next();
      log.debug(" ++++++++ "+pairs.getKey() + " = " + pairs.getValue());
      //			itUser.remove(); // avoids a ConcurrentModificationException

      ArrayList<Tuple<Integer,Integer>> ratedMoviesIndices = new ArrayList<Tuple<Integer,Integer>>();
      Iterator<Entry<Integer,Integer>> itMovie = pairs.getValue().getRatedMovies().entrySet().iterator();
      while (itMovie.hasNext()) {
        Map.Entry<Integer,Integer> movieRating = (Map.Entry<Integer,Integer>)itMovie.next();
        log.debug(" ++++++++ "+movieRating.getKey() + " = " + movieRating.getValue());
        //				itMovie.remove(); // avoids a ConcurrentModificationException

        // for each movie
        ratedMoviesIndices.add(new Tuple<Integer, Integer>(movieRating.getKey(),movieRating.getValue()));
      }
      // for each user
      if (isForTrain) {
        rbmSoftmax.trainRBMWeightMatrix(ratedMoviesIndices);
        this.successfullyTrainedThisRBM = true;
      } else {
        rbmSoftmax.predictUserPreference_VisibleToHiddenToVisible(ratedMoviesIndices, false);
      }
    }
  }

  private void updateClientWeightMatixForPrediction(RestrictedBoltzmannMachinesWithSoftmax rbmSoftmax) {	
    double[][][] trainedMwrbm = new double[this.numMovies+1][GlobalVariables.RBM_SIZE_HIDDEN_UNITS+1][GlobalVariables.RBM_SIZE_SOFTMAX];

    if (!this.successfullyTrainedThisRBM ) {
      log.debug("Didn't update the client weight matix for prediction, "
          +"cuz this newly weight matrix didn't trained curretly or it's null!!");
      trainedMwrbm = null;
    } else {
      log.debug("********Get client weight matrix for prediction..");
      double[][][] curMwrbm = rbmSoftmax.getTrainedWeightMatrix_RBM();
      for (int x=0; x<this.numMovies+1; x++) {
        for (int y=0; y<GlobalVariables.RBM_SIZE_HIDDEN_UNITS+1; y++) {
          String str = "";
          for (int z=0; z<GlobalVariables.RBM_SIZE_SOFTMAX; z++) {
            trainedMwrbm[x][y][z] = curMwrbm[x][y][z];
            str += " "+ String.valueOf(trainedMwrbm[x][y][z]);
          }
          log.debug(str+" softmax "); 
        }
        log.debug(" layer "); 
      }
      //			rbmSoftmax.printMatrix(this.numMovies+1, GlobalVariables.RBM_SIZE_HIDDEN_UNITS+1, GlobalVariables.RBM_SIZE_SOFTMAX, "clientWeightMatrix_RBM");
    }

    GlobalVariables.RBM_CLIENT_WEIGHTMATIX_FOR_PREDICT = new RBMClientWeightMatixForPredict(
      this.currentData.getKthRBM(),
      System.currentTimeMillis(),
      new HashMap<String, RBMMovieInfo>(this.currentData.getMovieHashMap()),
      trainedMwrbm,
      this.successfullyTrainedThisRBM,
      new ArrayList<String>(this.currentData.getMovieNameWithIdx())
        );
  }
  /*
  private void showData2Client() {
    String trainRBMsName= "ThreadRBMShowDataToClientPMins"; 
    Runnable showData2ClientRunnable= new ThreadRBMShowDataToClientPMins(trainRBMsName);
    Thread showData2ClientThread = new Thread(showData2ClientRunnable);

    log.debug("Starting "+ trainRBMsName +" at time "+System.currentTimeMillis());
    showData2ClientThread.start();

    log.debug(trainRBMsName+ " ends at "+System.currentTimeMillis());	
  }
   */

  private void printTrainingTestingData() {
    // For test
    log.debug("\n>>>>>>>>mmmmmmmKthRbm "+  this.currentData.getKthRBM());

    log.debug(">>>>>>>>DataQueueSize "+GlobalVariables.RBM_DATA_QUEUE.size());
    for (RBMDataQueueElementInfo item : GlobalVariables.RBM_DATA_QUEUE) {
      log.debug(">>>>>>>>"+item);
    }

    log.debug(">>>>>>>>numMovies "+ this.currentData.getMovieHashMap().size());
    Iterator<Entry<String, RBMMovieInfo>> itMovie = this.currentData.getMovieHashMap().entrySet().iterator();
    while (itMovie.hasNext()) {
      Map.Entry<String, RBMMovieInfo> pairs = (Map.Entry<String, RBMMovieInfo>)itMovie.next();
      log.debug(">>>>>>>>"+pairs.getKey() + " = " + pairs.getValue());
      //			itMovie.remove(); // avoids a ConcurrentModificationException
    }
    log.debug(">>>>>>>>numUsersForTrain "+ this.currentData.getUserHashMapTrain().size());
    Iterator<Entry<String, RBMUserInfo>> itUserTrain = this.currentData.getUserHashMapTrain().entrySet().iterator();
    while (itUserTrain.hasNext()) {
      Map.Entry<String, RBMUserInfo> pairs = (Map.Entry<String, RBMUserInfo>)itUserTrain.next();
      log.debug(">>>>>>>>"+pairs.getKey() + " = " + pairs.getValue());
      //			itUserTrain.remove(); // avoids a ConcurrentModificationException
    }
    log.debug(">>>>>>>>numUsersForTest "+ this.currentData.getUserHashMapTest().size());
    Iterator<Entry<String, RBMUserInfo>> itUserTest = this.currentData.getUserHashMapTest().entrySet().iterator();
    while (itUserTest.hasNext()) {
      Map.Entry<String, RBMUserInfo> pairs = (Map.Entry<String, RBMUserInfo>)itUserTest.next();
      log.debug(">>>>>>>>"+pairs.getKey() + " = " + pairs.getValue());
      //			itUserTest.remove(); // avoids a ConcurrentModificationException
    }
  }

  public static void main(String[] argv) throws IOException, ParseException, MovieDbException {

    InitializeWCR initWCR = new InitializeWCR();
    initWCR.getWiseCrowdRecConfigInfo();
    initWCR.themoviedbOrgInitial();
    initWCR.getFreebaseInfo();

    HashMap<String, RBMMovieInfo> movie = new HashMap<String, RBMMovieInfo>();
    HashMap<String, RBMUserInfo> userTrain = new HashMap<String, RBMUserInfo>();
    HashMap<String, RBMUserInfo> userTest = new HashMap<String, RBMUserInfo>();
    ArrayList<String> movieList = new ArrayList<String>();
    HashMap<Integer,Integer> rate;


    movie.put("The Weekend", new RBMMovieInfo(
      0, "/m/0bd5kxs", 1
        ));
    movie.put("Superman Returns", new RBMMovieInfo(
      1, "/m/044g_k", 2
        ));
    movieList.add(0, "The Weekend");
    movieList.add(1, "Superman Returns");

    rate = new HashMap<Integer,Integer>();
    rate.put(0, 1);
    userTrain.put("121", new RBMUserInfo(
      0, new HashMap<Integer,Integer>(rate)
        ));

    rate = new HashMap<Integer,Integer>();
    rate.put(0, 1);
    rate.put(1, 4);
    userTrain.put("111", new RBMUserInfo(
      0, new HashMap<Integer,Integer>(rate)
        ));

    rate = new HashMap<Integer,Integer>();
    rate.put(1, 1);
    userTrain.put("11", new RBMUserInfo(
      0, new HashMap<Integer,Integer>(rate)
        ));

    rate = new HashMap<Integer,Integer>();
    rate.put(1, 3);
    userTest.put("113", new RBMUserInfo(
      0, new HashMap<Integer,Integer>(rate)
        ));

    rate = new HashMap<Integer,Integer>();
    rate.put(0, 4);
    userTest.put("116", new RBMUserInfo(
      0, new HashMap<Integer,Integer>(rate)
        ));


    RBMDataQueueElementInfo curData = new RBMDataQueueElementInfo (
      0,
      new HashMap<String, RBMMovieInfo>(movie),
      new HashMap<String, RBMUserInfo>(userTrain),
      new HashMap<String, RBMUserInfo>(userTest),
      new ArrayList<String>(movieList)
        );

    ThreadRBMTrainingKMins t = new ThreadRBMTrainingKMins("testThread",
      new RBMDataQueueElementInfo(
        curData.getKthRBM(),
        new HashMap<String, RBMMovieInfo>(curData.getMovieHashMap()),
        new HashMap<String, RBMUserInfo>(curData.getUserHashMapTrain()),
        new HashMap<String, RBMUserInfo>(curData.getUserHashMapTest()),
        new ArrayList<String>(curData.getMovieNameWithIdx())
          )
        );
    t.printTrainingTestingData();
  }
}
