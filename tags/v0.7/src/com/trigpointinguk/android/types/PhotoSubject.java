package com.trigpointinguk.android.types;

public enum PhotoSubject {
	NOSUBJECT		(" ", ""),
	TRIGPOINT		("T", "The trigpoint"),
	FLUSHBRACKET	("F", "The flush bracket"),
	PEOPLE			("P", "People"),
	LANDSCAPE		("L", "Landscape"),
	OTHER			("O", "Other"),
	;

	private final String   code;
	private final String   descr;	
	PhotoSubject (String code, String descr) {
		this.code = code;
		this.descr  = descr;
	}
	public String code() {
		return code;
	}
	public String toString() {
		return descr;
	}
	public static PhotoSubject fromCode(String code) {  
		if (code != null) {
			for (PhotoSubject c : values()) {  
				if (code.equals(c.code)) {  
					return c;  
				}  
			}  
		}
		//throw new IllegalArgumentException("Invalid PhotoSubject: " + code); 
		return NOSUBJECT;
	}
}