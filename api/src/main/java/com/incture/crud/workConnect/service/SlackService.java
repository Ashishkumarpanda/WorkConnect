package com.incture.crud.workConnect.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.incture.crud.workConnect.entity.Task;
import com.incture.crud.workConnect.repository.TaskRepository;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SlackService {
	
	private static HashMap<String,String> users= new HashMap<String,String>();
    @Autowired
    private TaskRepository repository;
    
    @Value("${app.slack.token}")
    private String SlackToken;
    
    @Value("${app.slack.access}")
    private String SlackAccess;

    //Function to fetch multiple channel
    	public HashMap<String,String> getProjects() throws IOException, InterruptedException
    	{
    		HashMap<String,String> projects= new HashMap<String,String>();

    		String bot_token = this.SlackToken;
    		String token = "Bearer "+bot_token;
    		HttpResponse<String> response ;
    		HttpClient client = HttpClient.newBuilder().build();
    		HttpRequest request = HttpRequest.newBuilder()
                	.uri(URI.create("https://slack.com/api/conversations.list" + "?pretty=1"))
                	.header("Authorization",token)
                	.header("Content-Type","application/x-www-form-urlencoded")
                	.POST(HttpRequest.BodyPublishers.noBody())
                	.build();
    		
    		response = client.send(request,HttpResponse.BodyHandlers.ofString());
    		//System.out.println(response);
    		JSONObject taskObj = new JSONObject(response.body());
    		JSONArray taskArr = taskObj.getJSONArray("channels");
    		for(int i=0;i<taskArr.length();i++)
    		{
    			JSONObject obj = taskArr.getJSONObject(i);
    			projects.put(obj.getString("id"),obj.getString("name"));
    			
    		}
    		
    	return projects;
    }
    

    
    
    
    
    //Function to retrieve email associated with a Slack User Code
    public String userEmail(String code) throws IOException, InterruptedException {
		if(!users.containsKey(code)) {
			
			String bot_token = this.SlackToken;
			String token = "Bearer "+bot_token;
			HttpClient client = HttpClient.newBuilder().build();
			HttpRequest request = HttpRequest.newBuilder()
		                	.uri(URI.create("https://slack.com/api/users.profile.get" + "?user=" + code + "&pretty=1"))
		                	.header("Authorization",token)
		                	.header("Content-Type","application/x-www-form-urlencoded")
		                	.POST(HttpRequest.BodyPublishers.noBody())
		                	.build();
			HttpResponse<String> response;
		
			response = client.send(request,HttpResponse.BodyHandlers.ofString());
			
			String jsonString = response.body();
			JSONObject obj = new JSONObject(jsonString);
			users.put(code, obj.getJSONObject("profile").getString("email"));
			return obj.getJSONObject("profile").getString("email");

			
		}
		else {
			return users.get(code);
		}
    }
    
    //Service to check if a message is app mention or not
    public ArrayList<String> matchMention(String txt) throws IOException, InterruptedException {
    	ArrayList<String> res = new ArrayList<String>();
        String regex = "<@[A-Z0-9]+>";
        Pattern pattern = Pattern.compile(regex);
        Matcher m = pattern.matcher(txt);
        while (m.find()) {
        	String matchedText= m.group();
        	res.add(userEmail(matchedText.substring(2,matchedText.length()-1)));
        }
        return res;
    }
    
    
    //Service to fetch all the tasks from a channel
    public String fetchSlackData() throws IOException, InterruptedException {
    	
    	//List to store all the retrieved tasks to directly add in the repository
        ArrayList<Task> op = new ArrayList<Task>();
		
		//Text to send as a response to client
		String optxt = "";
		
        if(this.SlackAccess.equals("no")) {
        	return "No Access for Slack";
        }
        
        //Retrieve latest date to find all the tasks after that
        Date x = repository.retrieveSlackLast();
        
        //Slack Bot credentials
        String bot_token = this.SlackToken;
		String token = "Bearer "+bot_token;
		HashMap<String,String> projects = getProjects();
		

	    for(String key: projects.keySet()) {
	    	
			String channel_id = key;
			String url_str = "";
			if(projects.get(key).equals("general")) {
				
				continue;
			}
//			
			//Fetch task/messages assigned in the slack channel
			//For initial condition when no task has been added
	        if(x == null) {
	        	url_str = "https://slack.com/api/conversations.history" + "?channel=" + channel_id +"&pretty=1";
	        }
	        else {
	        	long ts = (x.getTime()/1000)+1;
	        	url_str = "https://slack.com/api/conversations.history" + "?channel=" + channel_id + "&oldest="+ String.valueOf(ts)+ "&pretty=1";
	        }
	        
			HttpClient client = HttpClient.newBuilder().build();
			HttpRequest request = HttpRequest.newBuilder()
			                .uri(URI.create(url_str))
			                .header("Authorization",token)
			                .header("Content-Type","application/x-www-form-urlencoded")
			                .POST(HttpRequest.BodyPublishers.noBody())
			                .build();
			HttpResponse<String> response;
			
			//Received response json for the requested tasks
			response = client.send(request,HttpResponse.BodyHandlers.ofString());		
			
			JSONObject taskObj = new JSONObject(response.body());
			JSONArray taskArr = taskObj.getJSONArray("messages");
	
	
	
				for(int i =0;i<taskArr.length();i++) {
					JSONObject tempObj = taskArr.getJSONObject(i);
					
					System.out.println(tempObj);
					System.out.println(projects.get(key));
					
					//Removes the bot messages
					try {
							if(tempObj.getString("user").equals("U030NQSKSCB")) {
								continue;
							}
					    } catch (Exception e) {
					      continue;
					    }

					
					//Fetching all the receiver(app mentioned) emails
					ArrayList<String> receiverEmails = matchMention(tempObj.getString("text"));
					
					//Checking for app mentions messages only
					if(tempObj.getString("type").equals("message") && receiverEmails.size()>0) {
						Date d = new Date(((long) Double.parseDouble(tempObj.getString("ts"))*1000));
						
						//Adding multiple tasks for multiple mentions
						for (String rem : receiverEmails) {
						
							//Creating and adding a new Task object to the output tasks array
							Task newTask = new Task();
							newTask.setTaskName(tempObj.getString("text"));
					        newTask.setReceiver(rem);
					        newTask.setPlatform("slack");
					        newTask.setStatus(0);
					        newTask.setProjectName(projects.get(key));
					        newTask.setIsDeleted(0);
					        newTask.setTime(d);
					        op.add(0,newTask);
						}
					}
				}
	    }
		
		//Adding tasks to database if tasks available
		if(op.size()>0) {
			optxt = "Added "+ op.size() +" Slack tasks to database";
			repository.saveAll(op);
		}
		else {
			optxt = "No slack task to add to database";
		}
		
		return optxt;
    }
}