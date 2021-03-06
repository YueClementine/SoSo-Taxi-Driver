/**
 * @Author 屠天宇
 * @CreateTime 2020/7/14
 * @UpdateTime 2020/7/19
 */
package com.sosotaxi.driver.ui.home;

import androidx.lifecycle.ViewModelProviders;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.sosotaxi.driver.R;

import com.sosotaxi.driver.adapter.UndoneOrderRecycleViewAdapter;
import com.sosotaxi.driver.common.CircleProgressBar;
import com.sosotaxi.driver.common.ProgressRunnable;
import com.sosotaxi.driver.common.TTSUtility;
import com.sosotaxi.driver.ui.driverOrder.DriverOrderActivity;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomeFragment extends Fragment{

    private HomeViewModel mViewModel;

    //司机评分
    private TextView mServicePointTextView;
    //司机流水
    private TextView mAccountTextView;
    //司机接单数
    private TextView mReceivingOrderQuantityTextView;
    //司机在线时长
    private TextView mOnlineTimeTextView;

    //未完成订单数目
    private TextView mUndoneOrderQuantityTextView;
    private RecyclerView mUndoneOrderRecycleView;
    private UndoneOrderRecycleViewAdapter mUndoneOrderRecycleViewAdapter;

    //模拟的出发地和目的地
    private List<String> startingPoints = new ArrayList<String>(Arrays.asList("人民艺术剧院", "天坛公园", "世贸天阶", " 三里屯"));
    private List<String> destinations = new ArrayList<String>(Arrays.asList("天安门广场","八宝山","圆明园","故宫"));
    private List<String> scheduleTime = new ArrayList<String>(Arrays.asList("2020年7月21日8：00","2020年7月21日17：00","2020年7月22日7：00","2020年7月22日22：00"));

    //听单动态效果
    private CircleProgressBar mCircleProgressBar;
    private TextView mStartOrderTextView;
    private Thread mDrawingCircleThread;
    private ProgressRunnable mProgressRunnable;

    private TextView mEndWorkTextView;

    private TTSUtility mTtsUtility;

    //模拟增加订单
    private Button testBtn;
    private List<String> testOrder = new ArrayList<String>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        mServicePointTextView = root.findViewById(R.id.firstpage_servicepoint_textView);
        mAccountTextView = root.findViewById(R.id.firstpage_account_textView);
        mReceivingOrderQuantityTextView = root.findViewById(R.id.firstpage_orderquantity_textView);

        mTtsUtility = TTSUtility.getInstance(getActivity().getApplicationContext());
        //连接一下，让后面的语音延迟小一些
        mTtsUtility.speaking(getString(R.string.slogan));


        testBtn = root.findViewById(R.id.test_order_btn);
        testBtn.setOnClickListener(new View.OnClickListener() {
            int index = 5;
            @Override
            public void onClick(View v) {
                testOrder.add("order"+index);
                startingPoints.add("出发点"+index);
                destinations.add("目的地"+index);
                mTtsUtility.speaking("已接到来自"+"order"+index+"的订单");
                index++;
                mUndoneOrderRecycleViewAdapter = new UndoneOrderRecycleViewAdapter(getContext(),startingPoints,destinations);
                mUndoneOrderRecycleViewAdapter.adapterListener = new AdapterListener() {
                    @Override
                    public void setListener() {
                        //setHearingOrderStartState();
                    }
                };
                mUndoneOrderRecycleView.setAdapter(mUndoneOrderRecycleViewAdapter);
                mUndoneOrderQuantityTextView.setText(String.valueOf(mUndoneOrderRecycleViewAdapter.getItemCount()));
            }
        });

        testBtn.setVisibility(View.INVISIBLE);

        mOnlineTimeTextView = root.findViewById(R.id.firstpage_onlinetime_textView);

        mUndoneOrderQuantityTextView = root.findViewById(R.id.firstpage_undone_orderquantity_textView);

        mUndoneOrderRecycleView = root.findViewById(R.id.first_page_undone_order_recycleView);
        mUndoneOrderRecycleView.setLayoutManager(new LinearLayoutManager(getContext()));
//        mUndoneOrderRecycleViewAdapter = new UndoneOrderRecycleViewAdapter(getContext());
        mUndoneOrderRecycleViewAdapter = new UndoneOrderRecycleViewAdapter(getContext(),startingPoints,destinations,scheduleTime);
        mUndoneOrderRecycleViewAdapter.adapterListener = new AdapterListener() {
            @Override
            public void setListener() {
                setHearingOrderStartState();
            }
        };
        mUndoneOrderRecycleView.setAdapter(mUndoneOrderRecycleViewAdapter);

        mUndoneOrderQuantityTextView.setText(String.valueOf(mUndoneOrderRecycleViewAdapter.getItemCount()));


        mEndWorkTextView = root.findViewById(R.id.end_work_textView);

        setEndWorkTextViewVisible(false);
        mCircleProgressBar = root.findViewById(R.id.circle_progress_bar);
        mStartOrderTextView = root.findViewById(R.id.start_order_textView);
        mProgressRunnable = new ProgressRunnable(mCircleProgressBar);
        mDrawingCircleThread = new Thread(mProgressRunnable);

        mStartOrderTextView.setOnClickListener(new View.OnClickListener() {
            boolean toggle = true;
           // Thread thread = new Thread(new testRunnable());
            @Override
            public void onClick(View v) {
                if(toggle || mStartOrderTextView.getText() == "开始听单"){
                    mStartOrderTextView.setText("听单中");
                    mTtsUtility.speaking("正在为您接受附近的订单");//为您接受附近的订单

                    new Thread(new orderHearingRunnable
                            ("奥体中心","天安门广场","11.5","27")).start();

                    setEndWorkTextViewVisible(true);
                    mDrawingCircleThread.start();
                    toggle = false;
                }else {
                    TTSUtility.getInstance(getActivity().getApplicationContext()).speaking("停止听单");
                    setHearingOrderStartState();
                    toggle = true;
                }
            }
        });

        //收车后续操作待定
        mEndWorkTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setHearingOrderStartState();
                mTtsUtility.speaking("已收车，停止听单");
                //Toast.makeText(getActivity().getApplicationContext(),"收车",Toast.LENGTH_SHORT).show();
            }
        });

        return root;
    }


    private void setHearingOrderStartState(){
        mStartOrderTextView.setText("开始听单");
        setEndWorkTextViewVisible(false);
        mProgressRunnable.setStop(true);
        mProgressRunnable = new ProgressRunnable(mCircleProgressBar);
        mDrawingCircleThread = new Thread(mProgressRunnable);
    }

    private void setEndWorkTextViewVisible(boolean toggle){
        if (toggle){
            mEndWorkTextView.setVisibility(View.VISIBLE);
        }else {
            mEndWorkTextView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mViewModel = ViewModelProviders.of(this).get(HomeViewModel.class);
        // TODO: Use the ViewModel
    }

    class orderHearingRunnable implements Runnable{
        private int index = 0;
        private String mStartingPoint;
        private String mDestination;
        private String mDistance;
        private String mCostTime;
        public orderHearingRunnable(){}
        
        public orderHearingRunnable(String mStartingPoint, String mDestination, String mDistance, String mCostTime) {
            this.mStartingPoint = mStartingPoint;
            this.mDestination = mDestination;
            this.mDistance = mDistance;
            this.mCostTime = mCostTime;
        }

        @Override
        public void run() {
            while (index < 5){
                try {
                    Thread.sleep(1000);
                    index ++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            mTtsUtility.speaking("已为您接到从"+mStartingPoint+"到"+mDestination+"的订单"+"预计行程"
                    +mDistance+"公里"+"时间"+mCostTime+"分钟");

            Intent intent = new Intent(getActivity().getApplicationContext(), DriverOrderActivity.class);
            startActivity(intent);

        }
    }

    class testRunnable implements Runnable{

        int length = testOrder.size();
        @Override
        public void run() {
            while (true){
                if (testOrder.size() != length){
                    length = testOrder.size();
                    System.out.println("接单"+length);

                }
            }
        }
    }
}