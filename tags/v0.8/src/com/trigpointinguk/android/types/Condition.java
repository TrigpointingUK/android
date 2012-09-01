package com.trigpointinguk.android.types;

import com.trigpointinguk.android.R;


public enum Condition {
	NOTLOGGED		("Z", R.drawable.c_nolog					, R.drawable.cs_nolog					, "Not Logged"),
	COULDNTFIND		("N", R.drawable.c_possiblymissing			, R.drawable.cs_possiblymissing			, "Couldn't Find"),
	GOOD			("G", R.drawable.c_good						, R.drawable.cs_good					, "Good"),
	SLIGHTLYDAMAGED	("S", R.drawable.c_slightlydamaged			, R.drawable.cs_slightlydamaged			, "Slightly Damaged"),
	CONVERTED		("C", R.drawable.c_slightlydamaged			, R.drawable.cs_slightlydamaged			, "Converted"),
	DAMAGED			("D", R.drawable.c_damaged					, R.drawable.cs_damaged					, "Damaged"),
	REMAINS			("R", R.drawable.c_toppled					, R.drawable.cs_toppled					, "Remains"),
	TOPPLED			("T", R.drawable.c_toppled					, R.drawable.cs_toppled					, "Toppled"),
	MOVED			("M", R.drawable.c_toppled					, R.drawable.cs_toppled					, "Moved"),
	POSSIBLYMISSING	("Q", R.drawable.c_possiblymissing			, R.drawable.cs_possiblymissing			, "Possibly Missing"),
	MISSING			("X", R.drawable.c_definitelymissing		, R.drawable.cs_definitelymissing		, "Destroyed"),
	VISIBLE			("V", R.drawable.c_unreachablebutvisible	, R.drawable.cs_unreachablebutvisible	, "Unreachable but Visible"),
	INACCESSIBLE	("P", R.drawable.c_unknown					, R.drawable.cs_unknown					, "Inaccessible"),
	UNKNOWN 		("U", R.drawable.c_unknown					, R.drawable.cs_unknown					, "Unknown"),
	;

	private final String   code;
	private final int	   icon;
	private final int	   icon_h;
	private final String   descr;	
	Condition(String code, int icon, int icon_h, String descr) {
		this.code 	= code;
		this.icon   = icon;
		this.icon_h = icon_h;
		this.descr  = descr;
	}
	public String code() {
		return code;
	}
	public int icon() {
		return icon;
	}
	public int icon(Boolean highlight) {
		if (highlight) {
			return icon_h;
		} else {
			return icon;
		}
	}
	public String toString() {
		return descr;
	}
	public static Condition fromCode(String code) {  
		if (code != null) {
			for (Condition c : values()) {  
				if (code.equals(c.code)) {  
					return c;  
				}  
			}  
		}
		//throw new IllegalArgumentException("Invalid condition: " + code); 
		return UNKNOWN;
	}
}