package org.sikrip;

import com.opencsv.CSVReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.None;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

/**
 * This class compares two laps based on their geographic locations and speeds.
 * It reads lap data from CSV files, converts latitude/longitude to UTM coordinates,
 * matches the second lap's speed to the closest point in the first lap,
 * and visualizes the results using XChart.
 */
public class LapComparisonByLocation {

    public static void main(String[] args) throws Exception {
        // === Load lap data from CSV ===
        final LapData lapA = readLatLonSpeedCsvFromResource("1m34.344s.csv");
        final LapData lapB = readLatLonSpeedCsvFromResource("1m53.819s.csv");

        // === Convert to UTM ===
        final double[][] xyA = latLonToUTM(lapA.lat, lapA.lon);
        final double[][] xyB = latLonToUTM(lapB.lat, lapB.lon);

        // === Match Lap B to closest point in Lap A ===
        final double[] speedBClosest = mapLapBToLapAByLocation(
            xyA[0], xyA[1], xyB[0], xyB[1], lapB.speed);

        // === Use Lap A sample index as x-axis ===
        final double[] indexA = new double[lapA.lat.length];
        for (int i = 0; i < indexA.length; i++) {
            indexA[i] = i;
        }

        // === Plot ===
        final XYChart chart = new XYChartBuilder()
            .width(800).height(500)
            .title("Lap Comparison by Location")
            .xAxisTitle("Sample Index")
            .yAxisTitle("Speed (m/s)")
            .build();

        chart.getStyler().setMarkerSize(4);

        chart.addSeries("Lap A", indexA, lapA.speed).setMarker(new None());
        chart.addSeries("Lap B (closest)", indexA, speedBClosest)
            .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
            .setMarker(new None());

        new SwingWrapper<>(chart).displayChart();
    }

    /**
     * Reads latitude, longitude, and speed data from a CSV file just for this sample/demo.
     */
    private static LapData readLatLonSpeedCsvFromResource(String resourceName) throws Exception {
        try (CSVReader reader = new CSVReader(
            new InputStreamReader(
                LapComparisonByLocation.class.getClassLoader().getResourceAsStream(resourceName)
            )
        )) {
            final List<Double> latList = new ArrayList<>();
            final List<Double> lonList = new ArrayList<>();
            final List<Double> speedList = new ArrayList<>();

            reader.readNext(); // skip header
            String[] line;
            while ((line = reader.readNext()) != null) {
                latList.add(Double.parseDouble(line[0]));
                lonList.add(Double.parseDouble(line[1]));
                speedList.add(Double.parseDouble(line[2]));
            }

            return new LapData(
                latList.stream().mapToDouble(d -> d).toArray(),
                lonList.stream().mapToDouble(d -> d).toArray(),
                speedList.stream().mapToDouble(d -> d).toArray()
            );
        }
    }

    /**
     * Converts latitude and longitude arrays to UTM coordinates (x/y).
     */
    private static double[][] latLonToUTM(double[] lat, double[] lon) {
        final CRSFactory crsFactory = new CRSFactory();
        final CoordinateReferenceSystem crsWGS84 = crsFactory.createFromName("epsg:4326");

        final int utmZone = (int) Math.floor((lon[0] + 180) / 6) + 1;
        final boolean isNorth = lat[0] >= 0;
        final String utmCode = "epsg:" + (isNorth ? "326" : "327") + String.format("%02d", utmZone);
        final CoordinateReferenceSystem crsUTM = crsFactory.createFromName(utmCode);

        final CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
        final CoordinateTransform transform = ctFactory.createTransform(crsWGS84, crsUTM);

        final double[] x = new double[lat.length];
        final double[] y = new double[lat.length];

        for (int i = 0; i < lat.length; i++) {
            final ProjCoordinate src = new ProjCoordinate(lon[i], lat[i]);
            final ProjCoordinate dst = new ProjCoordinate();
            transform.transform(src, dst);
            x[i] = dst.x;
            y[i] = dst.y;
        }

        return new double[][]{x, y};
    }

    /**
     * Maps the speed of Lap B to the closest point in Lap A based on their geographic locations.
     * The same can be done for other metrics like acceleration or etc.
     */
    private static double[] mapLapBToLapAByLocation(
        double[] xA, double[] yA,
        double[] xB, double[] yB,
        double[] speedB
    ) {
        final double[] result = new double[xA.length];

        for (int i = 0; i < xA.length; i++) {
            double minDist = Double.MAX_VALUE;
            int minIndex = -1;

            for (int j = 0; j < xB.length; j++) {
                double dx = xA[i] - xB[j];
                double dy = yA[i] - yB[j];
                double dist = dx * dx + dy * dy;

                if (dist < minDist) {
                    minDist = dist;
                    minIndex = j;
                }
            }

            result[i] = speedB[minIndex];
        }
        return result;
    }

    /**
     * Data structure to hold latitude, longitude, and speed arrays for a lap.
     */
    private static class LapData {
        public double[] lat, lon, speed;
        public LapData(double[] lat, double[] lon, double[] speed) {
            this.lat = lat;
            this.lon = lon;
            this.speed = speed;
        }
    }
}
