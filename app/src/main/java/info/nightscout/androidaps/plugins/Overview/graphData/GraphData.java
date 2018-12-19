package info.nightscout.androidaps.plugins.Overview.graphData;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.ValueDependentColor;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.Series;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.BasalData;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.AreaGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DoubleDataPoint;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.FixedLineGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.Scale;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.ScaledDataPoint;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.TimeAsXAxisLabelFormatter;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.Round;

/**
 * Created by mike on 18.10.2017.
 */

public class GraphData {
    private static Logger log = LoggerFactory.getLogger(L.OVERVIEW);

    private GraphView graph;
    public double maxY = Double.MIN_VALUE;
    public double minY = Double.MAX_VALUE;
    private List<BgReading> bgReadingsArray;
    private String units;
    private List<Series> series = new ArrayList<>();

    private IobCobCalculatorPlugin iobCobCalculatorPlugin;

    public GraphData(GraphView graph, IobCobCalculatorPlugin iobCobCalculatorPlugin) {
        units = ProfileFunctions.getInstance().getProfileUnits();
        this.graph = graph;
        this.iobCobCalculatorPlugin = iobCobCalculatorPlugin;
    }

    public void addBgReadings(long fromTime, long toTime, double lowLine, double highLine, List<BgReading> predictions) {
        double maxBgValue = Double.MIN_VALUE;
        //bgReadingsArray = MainApp.getDbHelper().getBgreadingsDataFromTime(fromTime, true);
        bgReadingsArray = iobCobCalculatorPlugin.getBgReadings();
        List<DataPointWithLabelInterface> bgListArray = new ArrayList<>();

        if (bgReadingsArray == null || bgReadingsArray.size() == 0) {
            if (L.isEnabled(L.OVERVIEW))
                log.debug("No BG data.");
            return;
        }

        for (BgReading bg : bgReadingsArray) {
            if (bg.date < fromTime || bg.date > toTime) continue;
            if (bg.value > maxBgValue) maxBgValue = bg.value;
            bgListArray.add(bg);
        }
        if (predictions != null) {
            Collections.sort(predictions, (o1, o2) -> Double.compare(o1.getX(), o2.getX()));
            for (BgReading prediction : predictions) {
                if (prediction.value >= 40)
                    bgListArray.add(prediction);
            }
        }

        maxBgValue = Profile.fromMgdlToUnits(maxBgValue, units);
        maxBgValue = units.equals(Constants.MGDL) ? Round.roundTo(maxBgValue, 40d) + 80 : Round.roundTo(maxBgValue, 2d) + 4;
        if (highLine > maxBgValue) maxBgValue = highLine;
        int numOfVertLines = units.equals(Constants.MGDL) ? (int) (maxBgValue / 40 + 1) : (int) (maxBgValue / 2 + 1);

        DataPointWithLabelInterface[] bg = new DataPointWithLabelInterface[bgListArray.size()];
        bg = bgListArray.toArray(bg);


        maxY = maxBgValue;
        minY = 0;
        // set manual y bounds to have nice steps
        graph.getGridLabelRenderer().setNumVerticalLabels(numOfVertLines);

        addSeries(new PointsWithLabelGraphSeries<>(bg));
    }

    public void addInRangeArea(long fromTime, long toTime, double lowLine, double highLine) {
        AreaGraphSeries<DoubleDataPoint> inRangeAreaSeries;

        DoubleDataPoint[] inRangeAreaDataPoints = new DoubleDataPoint[]{
                new DoubleDataPoint(fromTime, lowLine, highLine),
                new DoubleDataPoint(toTime, lowLine, highLine)
        };
        inRangeAreaSeries = new AreaGraphSeries<>(inRangeAreaDataPoints);
        inRangeAreaSeries.setColor(0);
        inRangeAreaSeries.setDrawBackground(true);
        inRangeAreaSeries.setBackgroundColor(MainApp.gc(R.color.inrangebackground));

        addSeries(inRangeAreaSeries);
    }

    // scale in % of vertical size (like 0.3)
    public void addBasals(long fromTime, long toTime, double scale) {
        LineGraphSeries<ScaledDataPoint> basalsLineSeries;
        LineGraphSeries<ScaledDataPoint> absoluteBasalsLineSeries;
        LineGraphSeries<ScaledDataPoint> baseBasalsSeries;
        LineGraphSeries<ScaledDataPoint> tempBasalsSeries;

        double maxBasalValueFound = 0d;
        Scale basalScale = new Scale();

        List<ScaledDataPoint> baseBasalArray = new ArrayList<>();
        List<ScaledDataPoint> tempBasalArray = new ArrayList<>();
        List<ScaledDataPoint> basalLineArray = new ArrayList<>();
        List<ScaledDataPoint> absoluteBasalLineArray = new ArrayList<>();
        double lastLineBasal = 0;
        double lastAbsoluteLineBasal = -1;
        double lastBaseBasal = 0;
        double lastTempBasal = 0;
        for (long time = fromTime; time < toTime; time += 60 * 1000L) {
            Profile profile = ProfileFunctions.getInstance().getProfile(time);
            if (profile == null) continue;
            BasalData basalData = iobCobCalculatorPlugin.getBasalData(profile, time);
            double baseBasalValue = basalData.basal;
            double absoluteLineValue = baseBasalValue;
            double tempBasalValue = 0;
            double basal = 0d;
            if (basalData.isTempBasalRunning) {
                absoluteLineValue = tempBasalValue = basalData.tempBasalAbsolute;
                if (tempBasalValue != lastTempBasal) {
                    tempBasalArray.add(new ScaledDataPoint(time, lastTempBasal, basalScale));
                    tempBasalArray.add(new ScaledDataPoint(time, basal = tempBasalValue, basalScale));
                }
                if (lastBaseBasal != 0d) {
                    baseBasalArray.add(new ScaledDataPoint(time, lastBaseBasal, basalScale));
                    baseBasalArray.add(new ScaledDataPoint(time, 0d, basalScale));
                    lastBaseBasal = 0d;
                }
            } else {
                if (baseBasalValue != lastBaseBasal) {
                    baseBasalArray.add(new ScaledDataPoint(time, lastBaseBasal, basalScale));
                    baseBasalArray.add(new ScaledDataPoint(time, basal = baseBasalValue, basalScale));
                    lastBaseBasal = baseBasalValue;
                }
                if (lastTempBasal != 0) {
                    tempBasalArray.add(new ScaledDataPoint(time, lastTempBasal, basalScale));
                    tempBasalArray.add(new ScaledDataPoint(time, 0d, basalScale));
                }
            }

            if (baseBasalValue != lastLineBasal) {
                basalLineArray.add(new ScaledDataPoint(time, lastLineBasal, basalScale));
                basalLineArray.add(new ScaledDataPoint(time, baseBasalValue, basalScale));
            }
            if (absoluteLineValue != lastAbsoluteLineBasal) {
                absoluteBasalLineArray.add(new ScaledDataPoint(time, lastAbsoluteLineBasal, basalScale));
                absoluteBasalLineArray.add(new ScaledDataPoint(time, basal, basalScale));
            }

            lastAbsoluteLineBasal = absoluteLineValue;
            lastLineBasal = baseBasalValue;
            lastTempBasal = tempBasalValue;
            maxBasalValueFound = Math.max(maxBasalValueFound, Math.max(tempBasalValue, baseBasalValue));
        }

        basalLineArray.add(new ScaledDataPoint(toTime, lastLineBasal, basalScale));
        baseBasalArray.add(new ScaledDataPoint(toTime, lastBaseBasal, basalScale));
        tempBasalArray.add(new ScaledDataPoint(toTime, lastTempBasal, basalScale));
        absoluteBasalLineArray.add(new ScaledDataPoint(toTime, lastAbsoluteLineBasal, basalScale));

        ScaledDataPoint[] baseBasal = new ScaledDataPoint[baseBasalArray.size()];
        baseBasal = baseBasalArray.toArray(baseBasal);
        baseBasalsSeries = new LineGraphSeries<>(baseBasal);
        baseBasalsSeries.setDrawBackground(true);
        baseBasalsSeries.setBackgroundColor(MainApp.gc(R.color.basebasal));
        baseBasalsSeries.setThickness(0);

        ScaledDataPoint[] tempBasal = new ScaledDataPoint[tempBasalArray.size()];
        tempBasal = tempBasalArray.toArray(tempBasal);
        tempBasalsSeries = new LineGraphSeries<>(tempBasal);
        tempBasalsSeries.setDrawBackground(true);
        tempBasalsSeries.setBackgroundColor(MainApp.gc(R.color.tempbasal));
        tempBasalsSeries.setThickness(0);

        ScaledDataPoint[] basalLine = new ScaledDataPoint[basalLineArray.size()];
        basalLine = basalLineArray.toArray(basalLine);
        basalsLineSeries = new LineGraphSeries<>(basalLine);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(MainApp.instance().getApplicationContext().getResources().getDisplayMetrics().scaledDensity * 2);
        paint.setPathEffect(new DashPathEffect(new float[]{2, 4}, 0));
        paint.setColor(MainApp.gc(R.color.basal));
        basalsLineSeries.setCustomPaint(paint);

        ScaledDataPoint[] absoluteBasalLine = new ScaledDataPoint[absoluteBasalLineArray.size()];
        absoluteBasalLine = absoluteBasalLineArray.toArray(absoluteBasalLine);
        absoluteBasalsLineSeries = new LineGraphSeries<>(absoluteBasalLine);
        Paint absolutePaint = new Paint();
        absolutePaint.setStyle(Paint.Style.STROKE);
        absolutePaint.setStrokeWidth(MainApp.instance().getApplicationContext().getResources().getDisplayMetrics().scaledDensity * 2);
        absolutePaint.setColor(MainApp.gc(R.color.basal));
        absoluteBasalsLineSeries.setCustomPaint(absolutePaint);

        basalScale.setMultiplier(maxY * scale / maxBasalValueFound);

        addSeries(baseBasalsSeries);
        addSeries(tempBasalsSeries);
        addSeries(basalsLineSeries);
        addSeries(absoluteBasalsLineSeries);
    }

    public void addTargetLine(long fromTime, long toTime, Profile profile) {
        LineGraphSeries<DataPoint> targetsSeries;

        Scale targetsScale = new Scale();
        targetsScale.setMultiplier(1);

        List<DataPoint> targetsSeriesArray = new ArrayList<>();
        double lastTarget = -1;

        if (LoopPlugin.lastRun != null && LoopPlugin.lastRun.constraintsProcessed != null) {
            APSResult apsResult = LoopPlugin.lastRun.constraintsProcessed;
            long latestPredictionsTime = apsResult.getLatestPredictionsTime();
            if (latestPredictionsTime > toTime) {
                toTime = latestPredictionsTime;
            }
        }

        for (long time = fromTime; time < toTime; time += 5 * 60 * 1000L) {
            TempTarget tt = TreatmentsPlugin.getPlugin().getTempTargetFromHistory(time);
            double value;
            if (tt == null) {
                value = (profile.getTargetLow(time) + profile.getTargetHigh(time)) / 2;
            } else {
                value = Profile.fromMgdlToUnits(tt.target(), profile.getUnits());
            }
            if (lastTarget != value) {
                if (lastTarget != -1)
                    targetsSeriesArray.add(new DataPoint(time, lastTarget));
                targetsSeriesArray.add(new DataPoint(time, value));
            }
            lastTarget = value;
        }
        targetsSeriesArray.add(new DataPoint(toTime, lastTarget));

        DataPoint[] targets = new DataPoint[targetsSeriesArray.size()];
        targets = targetsSeriesArray.toArray(targets);
        targetsSeries = new LineGraphSeries<>(targets);
        targetsSeries.setDrawBackground(false);
        targetsSeries.setColor(MainApp.gc(R.color.tempTargetBackground));
        targetsSeries.setThickness(2);

        addSeries(targetsSeries);
    }

    public void addTreatments(long fromTime, long endTime) {
        List<DataPointWithLabelInterface> filteredTreatments = new ArrayList<>();

        List<Treatment> treatments = TreatmentsPlugin.getPlugin().getTreatmentsFromHistory();

        for (int tx = 0; tx < treatments.size(); tx++) {
            Treatment t = treatments.get(tx);
            if (t.getX() < fromTime || t.getX() > endTime) continue;
            if (t.isSMB && !t.isValid) continue;
            t.setY(getNearestBg((long) t.getX()));
            filteredTreatments.add(t);
        }

        // ProfileSwitch
        List<ProfileSwitch> profileSwitches = TreatmentsPlugin.getPlugin().getProfileSwitchesFromHistory().getList();

        for (int tx = 0; tx < profileSwitches.size(); tx++) {
            DataPointWithLabelInterface t = profileSwitches.get(tx);
            if (t.getX() < fromTime || t.getX() > endTime) continue;
            filteredTreatments.add(t);
        }

        // Extended bolus
        if (!ConfigBuilderPlugin.getPlugin().getActivePump().isFakingTempsByExtendedBoluses()) {
            List<ExtendedBolus> extendedBoluses = TreatmentsPlugin.getPlugin().getExtendedBolusesFromHistory().getList();

            for (int tx = 0; tx < extendedBoluses.size(); tx++) {
                DataPointWithLabelInterface t = extendedBoluses.get(tx);
                if (t.getX() + t.getDuration() < fromTime || t.getX() > endTime) continue;
                if (t.getDuration() == 0) continue;
                t.setY(getNearestBg((long) t.getX()));
                filteredTreatments.add(t);
            }
        }

        // Careportal
        List<CareportalEvent> careportalEvents = MainApp.getDbHelper().getCareportalEventsFromTime(fromTime - 6 * 60 * 60 * 1000, true);

        for (int tx = 0; tx < careportalEvents.size(); tx++) {
            DataPointWithLabelInterface t = careportalEvents.get(tx);
            if (t.getX() + t.getDuration() < fromTime || t.getX() > endTime) continue;
            t.setY(getNearestBg((long) t.getX()));
            filteredTreatments.add(t);
        }

        DataPointWithLabelInterface[] treatmentsArray = new DataPointWithLabelInterface[filteredTreatments.size()];
        treatmentsArray = filteredTreatments.toArray(treatmentsArray);
        addSeries(new PointsWithLabelGraphSeries<>(treatmentsArray));
    }

    private double getNearestBg(long date) {
        if (bgReadingsArray == null)
            return Profile.fromMgdlToUnits(100, units);
        for (int r = 0; r < bgReadingsArray.size(); r++) {
            BgReading reading = bgReadingsArray.get(r);
            if (reading.date > date) continue;
            return Profile.fromMgdlToUnits(reading.value, units);
        }
        return bgReadingsArray.size() > 0
                ? Profile.fromMgdlToUnits(bgReadingsArray.get(0).value, units) : Profile.fromMgdlToUnits(100, units);
    }

    // scale in % of vertical size (like 0.3)
    public void addIob(long fromTime, long toTime, boolean useForScale, double scale) {
        FixedLineGraphSeries<ScaledDataPoint> iobSeries;
        List<ScaledDataPoint> iobArray = new ArrayList<>();
        Double maxIobValueFound = Double.MIN_VALUE;
        double lastIob = 0;
        Scale iobScale = new Scale();

        for (long time = fromTime; time <= toTime; time += 5 * 60 * 1000L) {
            Profile profile = ProfileFunctions.getInstance().getProfile(time);
            double iob = 0d;
            if (profile != null)
                iob = iobCobCalculatorPlugin.calculateFromTreatmentsAndTempsSynchronized(time, profile).iob;
            if (Math.abs(lastIob - iob) > 0.02) {
                if (Math.abs(lastIob - iob) > 0.2)
                    iobArray.add(new ScaledDataPoint(time, lastIob, iobScale));
                iobArray.add(new ScaledDataPoint(time, iob, iobScale));
                maxIobValueFound = Math.max(maxIobValueFound, Math.abs(iob));
                lastIob = iob;
            }
        }

        ScaledDataPoint[] iobData = new ScaledDataPoint[iobArray.size()];
        iobData = iobArray.toArray(iobData);
        iobSeries = new FixedLineGraphSeries<>(iobData);
        iobSeries.setDrawBackground(true);
        iobSeries.setBackgroundColor(0x80FFFFFF & MainApp.gc(R.color.iob)); //50%
        iobSeries.setColor(MainApp.gc(R.color.iob));
        iobSeries.setThickness(3);

        if (useForScale) {
            maxY = maxIobValueFound;
            minY = -maxIobValueFound;
        }

        iobScale.setMultiplier(maxY * scale / maxIobValueFound);

        addSeries(iobSeries);
    }

    // scale in % of vertical size (like 0.3)
    public void addCob(long fromTime, long toTime, boolean useForScale, double scale) {
        List<DataPointWithLabelInterface> minFailoverActiveList = new ArrayList<>();
        FixedLineGraphSeries<ScaledDataPoint> cobSeries;
        List<ScaledDataPoint> cobArray = new ArrayList<>();
        Double maxCobValueFound = 0d;
        int lastCob = 0;
        Scale cobScale = new Scale();

        for (long time = fromTime; time <= toTime; time += 5 * 60 * 1000L) {
            AutosensData autosensData = iobCobCalculatorPlugin.getAutosensData(time);
            if (autosensData != null) {
                int cob = (int) autosensData.cob;
                if (cob != lastCob) {
                    if (autosensData.carbsFromBolus > 0)
                        cobArray.add(new ScaledDataPoint(time, lastCob, cobScale));
                    cobArray.add(new ScaledDataPoint(time, cob, cobScale));
                    maxCobValueFound = Math.max(maxCobValueFound, cob);
                    lastCob = cob;
                }
                if (autosensData.failoverToMinAbsorbtionRate) {
                    autosensData.setScale(cobScale);
                    autosensData.setChartTime(time);
                    minFailoverActiveList.add(autosensData);
                }
            }
        }

        // COB
        ScaledDataPoint[] cobData = new ScaledDataPoint[cobArray.size()];
        cobData = cobArray.toArray(cobData);
        cobSeries = new FixedLineGraphSeries<>(cobData);
        cobSeries.setDrawBackground(true);
        cobSeries.setBackgroundColor(0x80FFFFFF & MainApp.gc(R.color.cob)); //50%
        cobSeries.setColor(MainApp.gc(R.color.cob));
        cobSeries.setThickness(3);

        if (useForScale) {
            maxY = maxCobValueFound;
            minY = 0;
        }

        cobScale.setMultiplier(maxY * scale / maxCobValueFound);

        addSeries(cobSeries);

        DataPointWithLabelInterface[] minFailover = new DataPointWithLabelInterface[minFailoverActiveList.size()];
        minFailover = minFailoverActiveList.toArray(minFailover);
        addSeries(new PointsWithLabelGraphSeries<>(minFailover));
    }

    // scale in % of vertical size (like 0.3)
    public void addDeviations(long fromTime, long toTime, boolean useForScale, double scale) {
        class DeviationDataPoint extends ScaledDataPoint {
            public int color;

            public DeviationDataPoint(double x, double y, int color, Scale scale) {
                super(x, y, scale);
                this.color = color;
            }
        }

        BarGraphSeries<DeviationDataPoint> devSeries;
        List<DeviationDataPoint> devArray = new ArrayList<>();
        Double maxDevValueFound = 0d;
        Scale devScale = new Scale();

        for (long time = fromTime; time <= toTime; time += 5 * 60 * 1000L) {
            AutosensData autosensData = iobCobCalculatorPlugin.getAutosensData(time);
            if (autosensData != null) {
                int color = MainApp.gc(R.color.deviationblack); // "="
                if (autosensData.type.equals("") || autosensData.type.equals("non-meal")) {
                    if (autosensData.pastSensitivity.equals("C"))
                        color = MainApp.gc(R.color.deviationgrey);
                    if (autosensData.pastSensitivity.equals("+"))
                        color = MainApp.gc(R.color.deviationgreen);
                    if (autosensData.pastSensitivity.equals("-"))
                        color = MainApp.gc(R.color.deviationred);
                } else if (autosensData.type.equals("uam")) {
                    color = MainApp.gc(R.color.uam);
                } else if (autosensData.type.equals("csf")) {
                    color = MainApp.gc(R.color.deviationgrey);
                }
                devArray.add(new DeviationDataPoint(time, autosensData.deviation, color, devScale));
                maxDevValueFound = Math.max(maxDevValueFound, Math.abs(autosensData.deviation));
            }
        }

        // DEVIATIONS
        DeviationDataPoint[] devData = new DeviationDataPoint[devArray.size()];
        devData = devArray.toArray(devData);
        devSeries = new BarGraphSeries<>(devData);
        devSeries.setValueDependentColor(new ValueDependentColor<DeviationDataPoint>() {
            @Override
            public int get(DeviationDataPoint data) {
                return data.color;
            }
        });

        if (useForScale) {
            maxY = maxDevValueFound;
            minY = -maxY;
        }

        devScale.setMultiplier(maxY * scale / maxDevValueFound);

        addSeries(devSeries);
    }

    // scale in % of vertical size (like 0.3)
    public void addRatio(long fromTime, long toTime, boolean useForScale, double scale) {
        LineGraphSeries<ScaledDataPoint> ratioSeries;
        List<ScaledDataPoint> ratioArray = new ArrayList<>();
        Double maxRatioValueFound = Double.MIN_VALUE;
        Double minRatioValueFound = Double.MAX_VALUE;
        Scale ratioScale = new Scale();

        for (long time = fromTime; time <= toTime; time += 5 * 60 * 1000L) {
            AutosensData autosensData = iobCobCalculatorPlugin.getAutosensData(time);
            if (autosensData != null) {
                ratioArray.add(new ScaledDataPoint(time, autosensData.autosensResult.ratio - 1, ratioScale));
                maxRatioValueFound = Math.max(maxRatioValueFound, autosensData.autosensResult.ratio - 1);
                minRatioValueFound = Math.min(minRatioValueFound, autosensData.autosensResult.ratio - 1);
            }
        }

        // RATIOS
        ScaledDataPoint[] ratioData = new ScaledDataPoint[ratioArray.size()];
        ratioData = ratioArray.toArray(ratioData);
        ratioSeries = new LineGraphSeries<>(ratioData);
        ratioSeries.setColor(MainApp.gc(R.color.ratio));
        ratioSeries.setThickness(3);

        if (useForScale) {
            maxY = Math.max(maxRatioValueFound, Math.abs(minRatioValueFound));
            minY = -maxY;
        }

        ratioScale.setMultiplier(maxY * scale / Math.max(maxRatioValueFound, Math.abs(minRatioValueFound)));

        addSeries(ratioSeries);
    }

    // scale in % of vertical size (like 0.3)
    public void addDeviationSlope(long fromTime, long toTime, boolean useForScale, double scale) {
        LineGraphSeries<ScaledDataPoint> dsMaxSeries;
        LineGraphSeries<ScaledDataPoint> dsMinSeries;
        List<ScaledDataPoint> dsMaxArray = new ArrayList<>();
        List<ScaledDataPoint> dsMinArray = new ArrayList<>();
        Double maxFromMaxValueFound = 0d;
        Double maxFromMinValueFound = 0d;
        Scale dsMaxScale = new Scale();
        Scale dsMinScale = new Scale();

        for (long time = fromTime; time <= toTime; time += 5 * 60 * 1000L) {
            AutosensData autosensData = iobCobCalculatorPlugin.getAutosensData(time);
            if (autosensData != null) {
                dsMaxArray.add(new ScaledDataPoint(time, autosensData.slopeFromMaxDeviation, dsMaxScale));
                dsMinArray.add(new ScaledDataPoint(time, autosensData.slopeFromMinDeviation, dsMinScale));
                maxFromMaxValueFound = Math.max(maxFromMaxValueFound, Math.abs(autosensData.slopeFromMaxDeviation));
                maxFromMinValueFound = Math.max(maxFromMinValueFound, Math.abs(autosensData.slopeFromMinDeviation));
            }
        }

        // Slopes
        ScaledDataPoint[] ratioMaxData = new ScaledDataPoint[dsMaxArray.size()];
        ratioMaxData = dsMaxArray.toArray(ratioMaxData);
        dsMaxSeries = new LineGraphSeries<>(ratioMaxData);
        dsMaxSeries.setColor(MainApp.gc(R.color.devslopepos));
        dsMaxSeries.setThickness(3);

        ScaledDataPoint[] ratioMinData = new ScaledDataPoint[dsMinArray.size()];
        ratioMinData = dsMinArray.toArray(ratioMinData);
        dsMinSeries = new LineGraphSeries<>(ratioMinData);
        dsMinSeries.setColor(MainApp.gc(R.color.devslopeneg));
        dsMinSeries.setThickness(3);

        if (useForScale) {
            maxY = Math.max(maxFromMaxValueFound, maxFromMinValueFound);
            minY = -maxY;
        }

        dsMaxScale.setMultiplier(maxY * scale / maxFromMaxValueFound);
        dsMinScale.setMultiplier(maxY * scale / maxFromMinValueFound);

        addSeries(dsMaxSeries);
        addSeries(dsMinSeries);
    }

    // scale in % of vertical size (like 0.3)
    public void addNowLine(long now) {
        LineGraphSeries<DataPoint> seriesNow;
        DataPoint[] nowPoints = new DataPoint[]{
                new DataPoint(now, 0),
                new DataPoint(now, maxY)
        };

        seriesNow = new LineGraphSeries<>(nowPoints);
        seriesNow.setDrawDataPoints(false);
        // custom paint to make a dotted line
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));
        paint.setColor(Color.WHITE);
        seriesNow.setCustomPaint(paint);

        addSeries(seriesNow);
    }

    public void formatAxis(long fromTime, long endTime) {
        graph.getViewport().setMaxX(endTime);
        graph.getViewport().setMinX(fromTime);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getGridLabelRenderer().setLabelFormatter(new TimeAsXAxisLabelFormatter("HH"));
        graph.getGridLabelRenderer().setNumHorizontalLabels(7); // only 7 because of the space
    }

    private void addSeries(Series s) {
        series.add(s);
    }

    public void performUpdate() {
        // clear old data
        graph.getSeries().clear();

        // add precalculated series
        for (Series s : series) {
            if (!s.isEmpty()) {
                s.onGraphViewAttached(graph);
                graph.getSeries().add(s);
            }
        }

        double step = 1d;
        if (maxY < 1) step = 0.1d;
        graph.getViewport().setMaxY(Round.ceilTo(maxY, step));
        graph.getViewport().setMinY(Round.floorTo(minY, step));
        graph.getViewport().setYAxisBoundsManual(true);

        // draw it
        graph.onDataChanged(false, false);
    }
}
