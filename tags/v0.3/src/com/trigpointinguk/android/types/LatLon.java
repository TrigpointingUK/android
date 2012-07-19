package com.trigpointinguk.android.types;

import java.io.Serializable;

import android.location.Location;

public class LatLon implements Serializable {
	/**
	 * Class to handle WGS location
	 *
	 *  
	 * calcOSGB and getOSGB.. methods borrow heavily from: http://www.jstott.me.uk/jcoord/ (c) 2006 Jonathan Stott
	 * 
	 */
	
	private static final long serialVersionUID = 7608960556753529471L;
	//private static final String TAG = "LatLon";
	private Double mLat;
	private Double mLon;
	private Double mEastings;
	private Double mNorthings;

	public static final double AIRYMAJ = 6377563.396;
	public static final double AIRYMIN = 6356256.909;
	public static final double AIRYECC = ((AIRYMAJ * AIRYMAJ) - (AIRYMIN * AIRYMIN)) / (AIRYMAJ * AIRYMAJ);
		
	public static final double WGSMAJ = 6378137.000;
	public static final double WGSMIN = 6356752.3141;
	public static final double WGSECC = ((WGSMAJ * WGSMAJ) - (WGSMIN * WGSMIN)) / (WGSMAJ * WGSMAJ);
		

	
	
	public LatLon() {
	}
	public LatLon(Double lat, Double lon) {
		this.mLat=lat;
		this.mLon=lon;
	}
	public LatLon(Location loc) {
		this.mLat = loc.getLatitude();
		this.mLon = loc.getLongitude();
	}
	public Double getLat() {
		return mLat;
	}
	public void setLat(Double mLat) {
		this.mLat = mLat;
	}
	public Double getLon() {
		return mLon;
	}
	public void setLon(Double mLon) {
		this.mLon = mLon;
	}
	

	
	
	public Double distanceTo(Double lat, Double lon) {
		Double d;
		if (lat == null || lon == null) {return null;}
		double lat1 = Math.toRadians(mLat);
		double lat2 = Math.toRadians(lat);
		double lon1 = Math.toRadians(mLon);
		double lon2 = Math.toRadians(lon);

		d = Math.acos(Math.sin(lat1)*Math.sin(lat2) + 
					  Math.cos(lat1)*Math.cos(lat2) *
					  Math.cos(lon2-lon1) ) * 6371;
		return d;
	}
	
	public Double distanceTo(LatLon l) {
		return distanceTo(l.mLat, l.mLon);
	}

	public Double distanceTo(Location l) {
		return distanceTo(l.getLatitude(), l.getLongitude());
	}
	
	public Double bearingTo(LatLon l) {
		Double b;
		double lat1 = Math.toRadians(mLat);
		double lat2 = Math.toRadians(l.mLat);
		double lon1 = Math.toRadians(mLon);
		double lon2 = Math.toRadians(l.mLon);
		
		double y = Math.sin(lon2-lon1) * Math.cos(lat2);
		double x = Math.cos(lat1) * Math.sin(lat2) -
		           Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2-lon1);
		b = Math.toDegrees( Math.atan2(y, x) );
		return b;
	}
	
 	public Double bearingFrom(Double lat, Double lon) {
		Double b;
		if (lat == null || lon == null) {return null;}
		double lat2 = Math.toRadians(mLat);
		double lat1 = Math.toRadians(lat);
		double lon2 = Math.toRadians(mLon);
		double lon1 = Math.toRadians(lon);
		
		double y = Math.sin(lon2-lon1) * Math.cos(lat2);
		double x = Math.cos(lat1) * Math.sin(lat2) -
		           Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2-lon1);
		b = Math.toDegrees( Math.atan2(y, x) );
		return b;
	}
 	
 	public Double bearingFrom(LatLon l) {
 		return bearingFrom(l.mLat, l.mLon);
	}
	
 	public Double bearingFrom(Location l) {
 		return bearingFrom(l.getLatitude(), l.getLongitude());
 	}
 	
	public String toString() {
	    return String.format("(%2.2f,%3.2f)", mLat, mLon);
	}	
	
	public String getWGS() {
		int latDegs = (int) Math.floor(Math.abs(mLat));
		Double latMins = (Math.abs(mLat)-latDegs)*60;
		int lonDegs = (int) Math.floor(Math.abs(mLon));
		Double LonMins = (Math.abs(mLon)-lonDegs)*60;
		return String.format("%s%02d %02.3f  %s%03d %02.3f", mLat>0?"N":"S", latDegs, latMins, mLon>0?"E":"W", lonDegs, LonMins );
	}
	
	public String getOSGB10 () {
		if (mEastings == null) {calcOSGB();}
		
		int hundredkmE = (int) Math.floor(mEastings / 100000);
		int hundredkmN = (int) Math.floor(mNorthings / 100000);
		String firstLetter;
		if (hundredkmN < 5) {
			if (hundredkmE < 5) {
				firstLetter = "S";
			} else {
				firstLetter = "T";
			}
		} else if (hundredkmN < 10) {
			if (hundredkmE < 5) {
				firstLetter = "N";
			} else {
				firstLetter = "O";
			}
		} else {
			firstLetter = "H";
		}

		int index = 65 + ((4 - (hundredkmN % 5)) * 5) + (hundredkmE % 5);
		// int ti = index;
		if (index >= 73)
			index++;
		String secondLetter = Character.toString((char) index);

		int e = (int) Math.floor(mEastings - (100000 * hundredkmE));
		int n = (int) Math.floor(mNorthings - (100000 * hundredkmN));

		return String.format("%s%s %05d %05d", firstLetter, secondLetter, e, n);
	}
	
	public String getOSGB6 () {
		String gridref10 = getOSGB10();
		String gridref6 = gridref10.substring(0,2) + gridref10.substring(3,6) + gridref10.substring(9,12);
		return gridref6;
	}

	
	
	private void calcOSGB() {
		double a = WGSMAJ;
		double eSquared = WGSECC;
		double phi = Math.toRadians(mLat);
		double lambda = Math.toRadians(mLon);
		double v = a / (Math.sqrt(1 - eSquared * sinSquared(phi)));
		double H = 0; // height
		double x = (v + H) * Math.cos(phi) * Math.cos(lambda);
		double y = (v + H) * Math.cos(phi) * Math.sin(lambda);
		double z = ((1 - eSquared) * v + H) * Math.sin(phi);

		double tx = -446.448;
		double ty = 124.157;
		double tz = -542.060;
		double s = 0.0000204894;
		double rx = Math.toRadians(-0.00004172222);
		double ry = Math.toRadians(-0.00006861111);
		double rz = Math.toRadians(-0.00023391666);

		double xB = tx + (x * (1 + s)) + (-rx * y) + (ry * z);
		double yB = ty + (rz * x) + (y * (1 + s)) + (-rx * z);
		double zB = tz + (-ry * x) + (rx * y) + (z * (1 + s));

		a = AIRYMAJ;
		eSquared = AIRYECC;

		double lambdaB = Math.toDegrees(Math.atan(yB / xB));
		double p = Math.sqrt((xB * xB) + (yB * yB));
		double phiN = Math.atan(zB / (p * (1 - eSquared)));
		for (int i = 1; i < 10; i++) {
			v = a / (Math.sqrt(1 - eSquared * sinSquared(phiN)));
			double phiN1 = Math.atan((zB + (eSquared * v * Math.sin(phiN))) / p);
			phiN = phiN1;
		}

		double phiB = Math.toDegrees(phiN);

		double osgbLat = phiB;
		double osgbLon = lambdaB;

		
		
		double OSGB_F0 = 0.9996012717;
		double N0 = -100000.0;
		double E0 = 400000.0;
		double phi0 = Math.toRadians(49.0);
		double lambda0 = Math.toRadians(-2.0);
		a = AIRYMAJ;
		double b = AIRYMIN;
		eSquared = AIRYECC;
		phi = Math.toRadians(osgbLat);
		lambda = Math.toRadians(osgbLon);
		double n = (a - b) / (a + b);
		v = a * OSGB_F0 * Math.pow(1.0 - eSquared * sinSquared(phi), -0.5);
		double rho =
			a * OSGB_F0 * (1.0 - eSquared)
			* Math.pow(1.0 - eSquared * sinSquared(phi), -1.5);
		double etaSquared = (v / rho) - 1.0;
		double M =
			(b * OSGB_F0)
			* (((1 + n + ((5.0 / 4.0) * n * n) + ((5.0 / 4.0) * n * n * n)) * (phi - phi0))
					- (((3 * n) + (3 * n * n) + ((21.0 / 8.0) * n * n * n))
							* Math.sin(phi - phi0) * Math.cos(phi + phi0))
							+ ((((15.0 / 8.0) * n * n) + ((15.0 / 8.0) * n * n * n))
									* Math.sin(2.0 * (phi - phi0)) * Math
									.cos(2.0 * (phi + phi0))) - (((35.0 / 24.0) * n * n * n)
											* Math.sin(3.0 * (phi - phi0)) * Math.cos(3.0 * (phi + phi0))));
		double I = M + N0;
		double II = (v / 2.0) * Math.sin(phi) * Math.cos(phi);
		double III =
			(v / 24.0) * Math.sin(phi) * Math.pow(Math.cos(phi), 3.0)
			* (5.0 - tanSquared(phi) + (9.0 * etaSquared));
		double IIIA =
			(v / 720.0)
			* Math.sin(phi)
			* Math.pow(Math.cos(phi), 5.0)
			* (61.0 - (58.0 * tanSquared(phi)) + Math.pow(Math.tan(phi),
					4.0));
		double IV = v * Math.cos(phi);
		double V =
			(v / 6.0) * Math.pow(Math.cos(phi), 3.0)
			* ((v / rho) - tanSquared(phi));
		double VI =
			(v / 120.0)
			* Math.pow(Math.cos(phi), 5.0)
			* (5.0 - (18.0 * tanSquared(phi))
					+ (Math.pow(Math.tan(phi), 4.0)) + (14 * etaSquared) - (58 * tanSquared(phi) * etaSquared));

		mNorthings =
			I + (II * Math.pow(lambda - lambda0, 2.0))
			+ (III * Math.pow(lambda - lambda0, 4.0))
			+ (IIIA * Math.pow(lambda - lambda0, 6.0));
		mEastings =
			E0 + (IV * (lambda - lambda0)) + (V * Math.pow(lambda - lambda0, 3.0))
			+ (VI * Math.pow(lambda - lambda0, 5.0));
	}


	protected static double sinSquared(double x) {
		return Math.sin(x) * Math.sin(x);
	}
	protected static double tanSquared(double x) {
		return Math.tan(x) * Math.tan(x);
	}

	}