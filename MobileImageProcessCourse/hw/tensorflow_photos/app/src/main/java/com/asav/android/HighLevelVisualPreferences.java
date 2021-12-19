package com.asav.android;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.os.Bundle;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by ....
 */

public class HighLevelVisualPreferences extends VisualPreferences{
    /** Tag for the {@link Log}. */
    private static final String TAG = "HLVisualPreferences";

    private int[] clut;
    protected String[] categoryList;

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preferences, container, false);
    }

    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        categoryList = getResources().getStringArray(R.array.category_list_age);

        clut=new int[categoryList.length-1];
        float[] hsv={0,1,1};
        for(int i=0;i<categoryList.length-1;++i){
            hsv[0]=360.0f* i / (categoryList.length-1);
            clut[i]=Color.HSVToColor(hsv);
            //Log.i(TAG,"Init color:"+clut[i]+" from hue "+hsv[0]);
        }
        super.onViewCreated(view,savedInstanceState);

        chart.setOnChartValueSelectedListener(this);
        updateChart();
    }
    @Override
    public void onValueSelected(Entry entry, Highlight highlight) {
        Log.i(TAG, "onValueSelected");
        BarEntry barEntry=(BarEntry)entry;
        IAxisValueFormatter formatter=chart.getXAxis().getValueFormatter();
        if(formatter!=null) {
            String category=formatter.getFormattedValue(entry.getX(), null);
            //Toast.makeText(getActivity(), category + " stack=" + highlight.getStackIndex(), Toast.LENGTH_SHORT).show();
            if(mainActivity==null)
                return;
            FragmentManager fm = getFragmentManager();

            Map<String,Set<String>> categoriesHistograms=getCategoriesHistograms();

            if(categoriesHistograms!=null && categoriesHistograms.containsKey(category)) {
                Set<String> filenames = categoriesHistograms.get(category);
                Photos photosFragment = new Photos();
                Bundle args = new Bundle();
                String[] titles=new String[1];
                titles[0] = category;
                args.putStringArrayList(titles[0], new ArrayList<String>(filenames));
                args.putStringArray("photosTaken", titles);
                photosFragment.setArguments(args);
                FragmentTransaction fragmentTransaction = fm.beginTransaction();
                fragmentTransaction.replace(R.id.fragment_switch, photosFragment);
                fragmentTransaction.addToBackStack(null);
                fragmentTransaction.commit();
            }
        }
    }

    @Override
    public void updateChart(){
        if(mainActivity!=null) {
            Map<String, Set<String>> categoriesHistograms = getCategoriesHistograms();
            //infoText.setText("");
            Map<String, Integer> histo = new HashMap<>();

            for (Map.Entry<String, Set<String>> entry : categoriesHistograms.entrySet()) {
                String key = entry.getKey();
                Set<String> value = entry.getValue();
                histo.put(key, value.size());
            }

            Map<String,Integer> sortedHisto=sortBySize(histo);
            final ArrayList<String> xLabel = new ArrayList<>();
            final List<BarEntry> entries = new ArrayList<BarEntry>();
            int index=0;
            List<String> keys=new ArrayList<>();
            for(String key:sortedHisto.keySet()) {
                if(sortedHisto.get(key)>0)
                    keys.add(key);
            }
            Collections.reverse(keys);
            for(String key : keys){
                xLabel.add(key);
                int value=(int)Math.round(sortedHisto.get(key));
                entries.add(new BarEntry(index, value));
                ++index;
            }
            if(!entries.isEmpty())
                chart.getAxisLeft().setAxisMaximum(entries.get(entries.size()-1).getY()+2);

            XAxis xAxis = chart.getXAxis();
            xAxis.setLabelCount(xLabel.size());
            xAxis.setValueFormatter(new IAxisValueFormatter() {
                @Override
                public String getFormattedValue(float value, AxisBase axis) {
                    //value=-value;
                    if (value>=0 && value<xLabel.size())
                        return xLabel.get((int)value);
                    else
                        return "";

                }
            });

            BarDataSet barDataSet = new BarDataSet(entries, "");
            barDataSet.setColor(color);

            BarData data = new BarData(barDataSet);
            data.setBarWidth(0.7f*xLabel.size()/categoryList.length);
            data.setValueFormatter(new IValueFormatter(){

                @Override
                public String getFormattedValue(float v, Entry entry, int i, ViewPortHandler viewPortHandler) {
                    return "" + ((int) v);
                }
            });
            chart.setData(data);
            chart.getLegend().setEnabled(false);
            chart.invalidate();
        }
    }

    private static <K, V extends Comparable<? super V>> Map<K, V> sortBySize(Map<K, V> map) {
        ArrayList<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
            @Override
            public int compare(Map.Entry<K, V> kvEntry, Map.Entry<K, V> t1) {
                return t1.getValue().compareTo(kvEntry.getValue());
            }
        });

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}