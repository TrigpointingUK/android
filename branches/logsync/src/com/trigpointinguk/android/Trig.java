package com.trigpointinguk.android;

import android.location.Location;

import com.trigpointinguk.android.common.LatLon;

public class Trig extends LatLon {
	/**
	 * Trigpoint class
	 */
	private static final long serialVersionUID = 1L;

	private String name;
	private String distance;
	private Double bearing;
	private int type;
	private Condition condition;
	private int found;
	
	
	// PHYSICAL TYPE of the trigpoint
	public enum Physical {
		ACTIVE	 		("AC", R.string.physical_AC),
		BERNTSEN		("BE", R.string.physical_BE),
		BLOCK			("BL", R.string.physical_BL),
		BOLT			("BO", R.string.physical_BO),
		BURIEDBLOCK		("BB", R.string.physical_BB),
		CANNON			("CA", R.string.physical_CA),
		CENTRE			("CE", R.string.physical_CE),
		CONCRETERING	("CR", R.string.physical_CR),
		CURRYSTOOL		("CS", R.string.physical_CS),
		FBM				("FB", R.string.physical_FB),
		FENOMARK		("FE", R.string.physical_FE),
		MONUMENT		("MO", R.string.physical_MO),
		OTHER			("OT", R.string.physical_OT),
		PILLAR			("PI", R.string.physical_PI),
		RIVET			("RI", R.string.physical_RI),
		SPIDER			("SP", R.string.physical_SP),
		SURFACEBLOCK	("SB", R.string.physical_SB),
		USERADDED		("UA", R.string.physical_UA)
		;

		private final String   code;
		private final int	   descr;	
		Physical (String code, int descr) {
			this.code = code;
			this.descr  = descr;
		}
		public String code() {
			return code;
		}
		public int descr() {
			return descr;
		}
		public static Physical fromCode(String code) {  
			if (code != null) {
				for (Physical c : values()) {  
					if (code.equals(c.code)) {  
						return c;  
					}  
				}  
			}
			return OTHER;
		}
	}

	// CURRENT USE of the trigpoint
	public enum Current {
		ACTIVE	 		("A", R.string.current_A),
		PASSIVE			("P", R.string.current_P),
		NONE			("N", R.string.current_N),
		USERADDED		("U", R.string.current_U)
		;

		private final String   code;
		private final int	   descr;	
		Current (String code, int descr) {
			this.code = code;
			this.descr  = descr;
		}
		public String code() {
			return code;
		}
		public int descr() {
			return descr;
		}
		public static Current fromCode(String code) {  
			if (code != null) {
				for (Current c : values()) {  
					if (code.equals(c.code)) {  
						return c;  
					}  
				}  
			}
			return NONE;
		}
	}

	
	// HISTORIC USE of the trigpoint
	public enum Historic {
		PRIMARY	 		("1", R.string.historic_1),
		SECONDARY		("2", R.string.historic_2),
		THIRDORDER		("3", R.string.historic_3),
		FOURTHORDER		("4", R.string.historic_4),
		FBM				("F", R.string.historic_F),
		GREATGLEN		("G", R.string.historic_G),
		HYDROGRAPHIC	("H", R.string.historic_H),
		NONE			("N", R.string.historic_N),
		OTHER			("O", R.string.historic_O),
		PASSIVE			("P", R.string.historic_P),
		EMILY			("E", R.string.historic_E),
		UNKNOWN			("Q", R.string.historic_Q),
		USERADDED		("U", R.string.historic_U)
		;

		private final String   code;
		private final int	   descr;	
		Historic (String code, int descr) {
			this.code = code;
			this.descr  = descr;
		}
		public String code() {
			return code;
		}
		public int descr() {
			return descr;
		}
		public static Historic fromCode(String code) {  
			if (code != null) {
				for (Historic c : values()) {  
					if (code.equals(c.code)) {  
						return c;  
					}  
				}  
			}
			return UNKNOWN;
		}
	}


	public Trig() {
	}
	public Trig(Double lat, Double lon) {
		super(lat, lon);
	}
	public Trig(Location loc) {
		super(loc);
	}
	public Trig(Double lat, Double lon, String name) {
		super(lat, lon);
		this.name = name;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	public int getFound() {
		return found;
	}
	public void setFound(int found) {
		this.found = found;
	}
	public Condition getCondition() {
		return condition;
	}
	public void setCondition(Condition condition) {
		this.condition = condition;
	}
	public String getDistance() {
		return distance;
	}
	public void setDistance(String distance) {
		this.distance = distance;
	}
	public Double getBearing() {
		return bearing;
	}
	public void setBearing(Double bearing) {
		this.bearing = bearing;
	}

}
