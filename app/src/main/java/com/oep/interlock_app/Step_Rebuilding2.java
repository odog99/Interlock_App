package com.oep.interlock_app;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.RadioButton;
import android.widget.Spinner;

import com.oep.owenslaptop.interlock_app.R;

import static com.oep.interlock_app.ViewValidity.updateViewValidity;

public class Step_Rebuilding2 extends AppCompatActivity {

    //setting up the spinners and the array
    private View[] views = new View[2];
    private Spinner locationSp;
    private Spinner roomSp;
    private RadioButton longTRB;
    public static String locationSt, easeAccSt, roomManSt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step__rebuilding2);

        //setup back button in title bar
        try {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }catch (NullPointerException npex){
            try {
                getActionBar().setDisplayHomeAsUpEnabled(true);
            }catch(NullPointerException ex){
                //back button not supported
            }
        }

        //setting up the GUI componenets
        locationSp = (Spinner) findViewById(R.id.locationSp);
        roomSp = (Spinner) findViewById(R.id.roomSp);
        longTRB = (RadioButton) findViewById(R.id.lTRB);
        final RadioButton skinnyGRB = (RadioButton) findViewById(R.id.sGRB);

        //adding the spinners to the array
        views[0] = locationSp;
        views[1] = roomSp;

        //when the size spinner is clicked
        locationSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View view, int arg2, long arg3) {
                int index = locationSp.getSelectedItemPosition();
                if (index != 0)
                    ViewValidity.updateViewValidity(locationSp);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                int index = locationSp.getSelectedItemPosition();
                if (index != 0)
                    ViewValidity.updateViewValidity(locationSp);
            }
        });

        //when the second size spinner is clicked
        roomSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View view, int arg2, long arg3) {
                int index = roomSp.getSelectedItemPosition();
                if (index != 0)
                    ViewValidity.updateViewValidity(roomSp);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                int index = roomSp.getSelectedItemPosition();
                if (index != 0)
                    ViewValidity.updateViewValidity(roomSp);
            }
        });
    }

    //when the FAB is clicked
    public void fabClicked(View view){
        if(ViewValidity.areViewsValid(views)) {
            //getting the input from the user
            locationSt = locationSp.getSelectedItem().toString();
            boolean easeAccB = longTRB.isChecked();
            roomManSt = roomSp.getSelectedItem().toString();
            if (easeAccB){
                easeAccSt = "Long thoroughfare";
            }
            else{
                easeAccSt = "Skinny gate";
            }
            //create a new intent (you do not get the current one because we do not need any
            // information from the home screen)
            Intent intent = new Intent(getApplicationContext(), Step_Rebuilding3.class);
            //this removes the animation
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            //extras--for passing data
            intent.putExtra("locationIndex", locationSp.getSelectedItemPosition());
            intent.putExtra("roomIndex", roomSp.getSelectedItemPosition());
            //start activity
            startActivity(intent);
        }else
            ViewValidity.updateViewValidity(views);
    }

    public boolean onOptionsItemSelected(MenuItem item){
        startActivity(new Intent(Step_Rebuilding2.this, Step_Rebuilding.class));
        return true;
    }
}