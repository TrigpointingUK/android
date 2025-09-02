package uk.trigpointing.android.types;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final double AIRYMAJ = 6377563.396;
    private static final double AIRYMIN = 6356256.909;
    private static final double AIRYECC = ((AIRYMAJ * AIRYMAJ) - (AIRYMIN * AIRYMIN)) / (AIRYMAJ * AIRYMAJ);
        
    private static final double WGSMAJ = 6378137.000;
    private static final double WGSMIN = 6356752.3141;
    private static final double WGSECC = ((WGSMAJ * WGSMAJ) - (WGSMIN * WGSMIN)) / (WGSMAJ * WGSMAJ);
        
    public enum UNITS {KM, MILES, METRES, YARDS}


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
    public LatLon(Long eastings, Long northings) {
        this.mEastings = Double.valueOf(eastings);
        this.mNorthings = Double.valueOf(northings);
        calcWGS();
    }
    public LatLon(String osgbGridref) throws IllegalArgumentException {
        if (osgbGridref == null || osgbGridref.equals("")) {
            throw new IllegalArgumentException("");
        }
        
        osgbGridref = Pattern.compile("\\s").matcher(osgbGridref).replaceAll("");
        Pattern pattern = Pattern.compile("(\\w\\w)(\\d{6,10})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(osgbGridref.toUpperCase(Locale.ROOT));
        
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Gridref : invalid format");
        }
        
        String gridletters = matcher.group(1);
        String digits = matcher.group(2);
        Long eastings;
        Long northings;
        switch (digits.length()) {
        case 6:
            eastings =  100 * Long.parseLong(digits.substring(0, digits.length()/2));
            northings = 100 * Long.parseLong(digits.substring(digits.length()/2));
            break;
        case 8:
            eastings =   10 * Long.parseLong(digits.substring(0, digits.length()/2));
            northings =  10 * Long.parseLong(digits.substring(digits.length()/2));
            break;    
        case 10:    
            eastings =        Long.parseLong(digits.substring(0, digits.length()/2));
            northings =       Long.parseLong(digits.substring(digits.length()/2));
            break;
        default:
            throw new IllegalArgumentException("Gridref : incorrect digit count");                
        }
        
        int big = gridletters.charAt(0) - 'A';
        if (big > 7) {big --;} // no I
        int bigr   = big / 5;
        int bigc   = big % 5;

        int small  = gridletters.charAt(1) - 'A';
        if (small > 7) {small --;} // no I
        int smallr = small / 5;
        int smallc  = small % 5;
        
        eastings += 500000* (bigc-2) + 100000*(smallc);
        northings += 500000L * (3-bigr) + 100000*(4-smallr);

        mEastings = Double.valueOf(eastings);
        mNorthings = Double.valueOf(northings);
        calcWGS();
    }

    
    
    public Double getLat() {
        return mLat;
    }
    public void setLat(Double mLat) {
        this.mLat = mLat;
        mEastings = null; mNorthings = null;
    }
    public Double getLon() {
        return mLon;
    }
    public void setLon(Double mLon) {
        this.mLon = mLon;
        mEastings = null; mNorthings = null;
    }
    
    

    
    
    public Long getEastings() {
        return mEastings.longValue();
    }
    public Long getNorthings() {
        return mNorthings.longValue();
    }
    
    
    public Double distanceTo(Double lat, Double lon, UNITS units) {
        Double d;
        double radius = 0;
        if (lat == null || lon == null) {return null;}
        double lat1 = Math.toRadians(mLat);
        double lat2 = Math.toRadians(lat);
        double lon1 = Math.toRadians(mLon);
        double lon2 = Math.toRadians(lon);

        switch (units) {
        case KM:
            radius = 6371;
            break;
        case MILES:
            radius = 3959;
            break;
        case METRES:
            radius = 6371000;
            break;
        case YARDS:
            radius = 3959*1760;
            break;
        }
        
        d = Math.acos(Math.sin(lat1)*Math.sin(lat2) + 
                      Math.cos(lat1)*Math.cos(lat2) *
                      Math.cos(lon2-lon1) ) * radius;
        return d;
    }
    
    public Double distanceTo(LatLon l, UNITS u) {
        return distanceTo(l.mLat, l.mLon, u);
    }

    public Double distanceTo(Location l, UNITS u) {
        return distanceTo(l.getLatitude(), l.getLongitude(), u);
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
        
        // Normalize bearing to 0-360 degrees
        if (b < 0) {
            b += 360.0;
        }
        
        return b;
    }
     
     public Double bearingFrom(LatLon l) {
         return bearingFrom(l.mLat, l.mLon);
    }
    
     public Double bearingFrom(Location l) {
         return bearingFrom(l.getLatitude(), l.getLongitude());
     }
     
    public String toString() {
        return String.format(Locale.getDefault(), "(%2.2f,%3.2f)", mLat, mLon);
    }    
    
    public String getWGS() {
        int latDegs = (int) Math.floor(Math.abs(mLat));
        Double latMins = (Math.abs(mLat)-latDegs)*60;
        int lonDegs = (int) Math.floor(Math.abs(mLon));
        Double LonMins = (Math.abs(mLon)-lonDegs)*60;
        return String.format(Locale.getDefault(), "%s%02d %02.3f  %s%03d %02.3f", mLat>0?"N":"S", latDegs, latMins, mLon>0?"E":"W", lonDegs, LonMins );
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

        return String.format(Locale.getDefault(), "%s%s %05d %05d", firstLetter, secondLetter, e, n);
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
        double ty = 125.157;
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

    
    
    
    // convert eastings, northings to latitude, longitude
    public void calcWGS() {
        // convert eastings, northings to Airy latitude longitude
        double OSGB_F0 = 0.9996012717;
        double N0 = -100000.0;
        double E0 = 400000.0;
        double phi0 = Math.toRadians(49.0);
        double lambda0 = Math.toRadians(-2.0);
        double a = AIRYMAJ;
        double b = AIRYMIN;
        double eSquared = AIRYECC;
        double phi = 0.0;
        double lambda = 0.0;
        double E = mEastings;
        double N = mNorthings;
        double n = (a - b) / (a + b);
        double M = 0.0;
        double phiPrime = ((N - N0) / (a * OSGB_F0)) + phi0;
        do {
          M = (b * OSGB_F0)
              * (((1 + n + ((5.0 / 4.0) * n * n) + ((5.0 / 4.0) * n * n * n)) * (phiPrime - phi0))
                  - (((3 * n) + (3 * n * n) + ((21.0 / 8.0) * n * n * n))
                      * Math.sin(phiPrime - phi0) * Math.cos(phiPrime + phi0))
                  + ((((15.0 / 8.0) * n * n) + ((15.0 / 8.0) * n * n * n))
                      * Math.sin(2.0 * (phiPrime - phi0)) * Math
                      .cos(2.0 * (phiPrime + phi0))) - (((35.0 / 24.0) * n * n * n)
                  * Math.sin(3.0 * (phiPrime - phi0)) * Math
                  .cos(3.0 * (phiPrime + phi0))));
          phiPrime += (N - N0 - M) / (a * OSGB_F0);
        } while ((N - N0 - M) >= 0.001);
        double v = a * OSGB_F0
            * Math.pow(1.0 - eSquared * sinSquared(phiPrime), -0.5);
        double rho = a * OSGB_F0 * (1.0 - eSquared)
            * Math.pow(1.0 - eSquared * sinSquared(phiPrime), -1.5);
        double etaSquared = (v / rho) - 1.0;
        double VII = Math.tan(phiPrime) / (2 * rho * v);
        double VIII = (Math.tan(phiPrime) / (24.0 * rho * Math.pow(v, 3.0)))
            * (5.0 + (3.0 * tanSquared(phiPrime)) + etaSquared - (9.0 * tanSquared(phiPrime) * etaSquared));
        double IX = (Math.tan(phiPrime) / (720.0 * rho * Math.pow(v, 5.0)))
            * (61.0 + (90.0 * tanSquared(phiPrime)) + (45.0 * tanSquared(phiPrime) * tanSquared(phiPrime)));
        double X = sec(phiPrime) / v;
        double XI = (sec(phiPrime) / (6.0 * v * v * v))
            * ((v / rho) + (2 * tanSquared(phiPrime)));
        double XII = (sec(phiPrime) / (120.0 * Math.pow(v, 5.0)))
            * (5.0 + (28.0 * tanSquared(phiPrime)) + (24.0 * tanSquared(phiPrime) * tanSquared(phiPrime)));
        double XIIA = (sec(phiPrime) / (5040.0 * Math.pow(v, 7.0)))
            * (61.0 + (662.0 * tanSquared(phiPrime))
                + (1320.0 * tanSquared(phiPrime) * tanSquared(phiPrime)) + (720.0
                * tanSquared(phiPrime) * tanSquared(phiPrime) * tanSquared(phiPrime)));
        phi = phiPrime - (VII * Math.pow(E - E0, 2.0))
            + (VIII * Math.pow(E - E0, 4.0)) - (IX * Math.pow(E - E0, 6.0));
        lambda = lambda0 + (X * (E - E0)) - (XI * Math.pow(E - E0, 3.0))
            + (XII * Math.pow(E - E0, 5.0)) - (XIIA * Math.pow(E - E0, 7.0));
        
        
        // change datum to WGS84
        double v1 = a / (Math.sqrt(1 - eSquared * sinSquared(phi)));
        double H = 0; // height
        double x = (v1 + H) * Math.cos(phi) * Math.cos(lambda);
        double y = (v1 + H) * Math.cos(phi) * Math.sin(lambda);
        double z = ((1 - eSquared) * v1 + H) * Math.sin(phi);

        double tx = 446.448;
        double ty = -125.157;
        double tz = 542.060;
        double s = -0.0000204894;
        double rx = Math.toRadians(0.00004172222);
        double ry = Math.toRadians(0.00006861111);
        double rz = Math.toRadians(0.00023391666);

        double xB = tx + (x * (1 + s)) + (-rx * y) + (ry * z);
        double yB = ty + (rz * x) + (y * (1 + s)) + (-rx * z);
        double zB = tz + (-ry * x) + (rx * y) + (z * (1 + s));

        a = WGSMAJ;
        eSquared = WGSECC;

        double lambdaB = Math.toDegrees(Math.atan(yB / xB));
        double p = Math.sqrt((xB * xB) + (yB * yB));
        double phiN = Math.atan(zB / (p * (1 - eSquared)));
        for (int i = 1; i < 10; i++) {
          v1 = a / (Math.sqrt(1 - eSquared * sinSquared(phiN)));
          double phiN1 = Math.atan((zB + (eSquared * v1 * Math.sin(phiN))) / p);
          phiN = phiN1;
        }

        double phiB = Math.toDegrees(phiN);

        mLat = phiB;
        mLon = lambdaB;


    }

    protected static double sinSquared(double x) {
        return Math.sin(x) * Math.sin(x);
    }
    protected static double tanSquared(double x) {
        return Math.tan(x) * Math.tan(x);
    }
    protected static double sec(double x) {
        return 1.0 / Math.cos(x);
    }


    
    }