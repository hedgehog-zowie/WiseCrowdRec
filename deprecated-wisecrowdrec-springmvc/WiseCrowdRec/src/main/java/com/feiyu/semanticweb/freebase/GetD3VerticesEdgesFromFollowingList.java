package com.feiyu.semanticweb.freebase;
/**
 * @author feiyu
 */

import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import twitter4j.PagableResponseList;
import twitter4j.TwitterException;
import twitter4j.User;

import com.feiyu.utils.GlobalVariables;
import com.feiyu.utils.InitializeWCR;
import com.jayway.jsonpath.JsonPath;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

public class GetD3VerticesEdgesFromFollowingList {
  private static Logger log = Logger.getLogger(GetD3VerticesEdgesFromFollowingList.class.getName());
  private JSONObject d3Data = new JSONObject();
  private JSONParser parser = new JSONParser();
  private JSONArray d3Vertices = new JSONArray();
  private JSONObject d3Vertex;	
  private JSONArray d3Edges = new JSONArray();
  private JSONObject d3Edge;	
  private int actorMaxIdx = -1;
  private int movieMaxIdx = -1;
  private int genreMaxIdx = -1;
  private HashMap<String, Integer> actorIdxJson = new HashMap<String, Integer>();
  private HashMap<String, Integer> movieIdxJson = new HashMap<String, Integer>();
  private HashMap<String, Integer> genreIdxJson = new HashMap<String, Integer>();

  @SuppressWarnings("unchecked")
  public void getVerticesEdgesInJson(String userID) 
      throws NumberFormatException, ConnectionException, TwitterException, IOException, ParseException {
    FollowingWhom followingWhom = new FollowingWhom();
    PagableResponseList<User> friendList = followingWhom.getFollowingWhomList(userID);

    System.out.println( userID +" is following:");
    for (User user : friendList) {
      System.out.println(
        "user->"+user.getName()
        +"---"+user.getScreenName()
        +"---"+user.getId()
          );
      this.getD3VertexEdgeActorMovies(user.getName());
    }

    log.debug(d3Vertices);
    log.debug(d3Edges);
    d3Data.put("nodes", d3Vertices);
    d3Data.put("links", d3Edges);

    this.sendMessage(d3Data.toString());
  }

  @SuppressWarnings("unchecked")
  public void getD3VertexEdgeActorMovies(String personName) throws IOException, ParseException {
    JSONObject response = (JSONObject)parser.parse(
      GlobalVariables.FREEBASE_GET_ACTOR_MOVIES.getMovieListByActorName(personName, false));
    JSONArray results = (JSONArray)response.get("result");

    if (results.size() > 0 && !this.actorIdxJson.containsKey(personName)) {
      log.debug("Put actor " + personName +" into JSONArray d3Vertices..");

      d3Vertex = new JSONObject();	
      d3Vertex.put("name", personName.substring(
        0, personName.length()<3?personName.length():3));
      d3Vertex.put("fullname", personName);
      d3Vertex.put("entity", "actor");

      ++actorMaxIdx;

      d3Vertices.add(d3Vertex);
      //			this.sendMessage(d3Vertex.toString());

      this.actorIdxJson.put(personName, actorMaxIdx);
    }

    movieMaxIdx = this.d3Vertices.size()-1;	
    for (Object result : results) {
      String movieName = JsonPath.read(result,"$.name").toString();
      int curMovieIdxInJson = -1;
      if (!this.movieIdxJson.containsKey(movieName)) {
        log.debug("Put movie " + movieName +" into JSONArray d3Vertices..");

        d3Vertex = new JSONObject();	
        d3Vertex.put("name", movieName.substring(
          0, movieName.length()<3?movieName.length():3));
        d3Vertex.put("fullname", movieName);
        d3Vertex.put("entity", "movie");

        curMovieIdxInJson = ++movieMaxIdx;
        d3Vertices.add(d3Vertex);
        //				this.sendMessage(d3Vertex.toString());

        this.movieIdxJson.put(movieName, curMovieIdxInJson);
      } else {
        curMovieIdxInJson = this.movieIdxJson.get(movieName);
      }

      log.debug("Put actor-movie link into JSONArray d3Edges..");
      d3Edge= new JSONObject();	
      d3Edge.put("source", actorMaxIdx);
      d3Edge.put("target", curMovieIdxInJson);
      d3Edge.put("type", "linkactormovie");
      d3Edges.add(d3Edge);
      //			this.sendMessage(d3Edge.toString());

      this.getD3VertexEdgeMovieGenres(personName, movieName);
      movieMaxIdx = this.d3Vertices.size()-1;	
    }
    actorMaxIdx = this.d3Vertices.size()-1;

    log.debug(d3Vertices);
    log.debug(d3Edges);
    d3Data.put("nodes", d3Vertices);
    d3Data.put("links", d3Edges);
    log.debug(d3Data);
  }

  @SuppressWarnings("unchecked")
  private void getD3VertexEdgeMovieGenres(String actorName, String movieName) throws ParseException, IOException {
    JSONObject response = (JSONObject)parser.parse(
      GlobalVariables.FREEBASE_GET_ACTOR_MOVIES.getFilmGenresByActorNMovieName(actorName, movieName, false));
    JSONArray results = (JSONArray)response.get("result");
    for (Object result : results) {
      //				System.out.println(JsonPath.read(result,"$.starring[*].actor").toString());
      String[] genres = JsonPath.read(result,"$.genre").toString()
          .replace("[\"", "")
          .replace("\"]", "")
          .split("\",\"");
      genreMaxIdx = this.d3Vertices.size()-1;
      for (String genre : genres) {
        int curGenreIdxInJson = -1;
        if (!this.genreIdxJson.containsKey(genre)) {
          log.info("Put genre " + genre +" into JSONArray d3Vertices..");

          d3Vertex = new JSONObject();	
          d3Vertex.put("name", genre.substring(
            0, genre.length()<3?genre.length():3));
          d3Vertex.put("fullname", genre);
          d3Vertex.put("entity", "genre");

          curGenreIdxInJson = ++genreMaxIdx;
          d3Vertices.add(d3Vertex);
          //					this.sendMessage(d3Vertex.toString());
          this.genreIdxJson.put(genre, curGenreIdxInJson);
        } else {
          curGenreIdxInJson = this.genreIdxJson.get(genre);
        }

        log.info("Put movie-genre link into JSONArray d3Edges..");
        d3Edge= new JSONObject();	
        d3Edge.put("source", this.movieIdxJson.get(movieName));
        d3Edge.put("target", curGenreIdxInJson);
        d3Edge.put("type", "linkmoviegenre");
        d3Edges.add(d3Edge);
        //				this.sendMessage(d3Edge.toString());
      }
    }
  }

  private void sendMessage(String msg) throws IOException {
    GlobalVariables.RABBITMQ_CHANNEL.basicPublish("", GlobalVariables.RABBITMQ_QUEUE_NAME_SMCSUBGRAPH, null, msg.getBytes());
    log.info(" [x] RABBITMQ_QUEUE_NAME_SMCSUBGRAPH: message Sent to queue buffer: " + msg);
  }

  public static void main(String[] argv) throws NumberFormatException, ConnectionException, TwitterException, IOException, ParseException {
    InitializeWCR iniWcr = new InitializeWCR();
    iniWcr.getWiseCrowdRecConfigInfo();
    iniWcr.getFreebaseInfo();
    GetD3VerticesEdgesFromFollowingList getVEFromFL = new GetD3VerticesEdgesFromFollowingList();
    //		getVEFromFL.getVerticesEdgesInJson("");
    getVEFromFL.getD3VertexEdgeActorMovies("Kiefer Sutherland");
  }
}
