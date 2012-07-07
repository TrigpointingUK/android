package com.trigpointinguk.android;

public class TrigLog {
	private String 		date;
	private String 		username;
	private String 		text;
	private Condition   condition;
	
	
	public TrigLog(String username, String date, Condition condition, String text) {
		super();
		this.username = username;
		this.date = date;
		this.condition = condition; 
		this.text = text;
	}
	
	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}

	public void setCondition(Condition condition) {
		this.condition = condition;
	}
	public Condition getCondition() {
		return condition;
	}
}
