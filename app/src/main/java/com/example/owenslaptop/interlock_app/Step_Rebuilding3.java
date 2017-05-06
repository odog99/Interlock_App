package com.example.owenslaptop.interlock_app;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import static com.example.owenslaptop.interlock_app.ViewValidity.areViewsValid;
import static com.example.owenslaptop.interlock_app.ViewValidity.updateViewValidity;

public class Step_Rebuilding3 extends AppCompatActivity {

    //NEED TO FIND REASON WHY THAT THE BUTTONS ARE NOT DEFAULT SELECTED

    //setting up the spinners and the array
    private View[] views = new View[1];
    private Spinner stepsGluedSp;
    private RadioButton treeYRB, treeNRB, clipsYRB, clipsNRB, lineYRB, lineNRB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step__rebuilding3);

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
        stepsGluedSp = (Spinner) findViewById(R.id.stepsGluedSp);
        treeYRB = (RadioButton) findViewById(R.id.tRYRB);
        treeNRB = (RadioButton) findViewById(R.id.tRNRB);
        clipsYRB = (RadioButton) findViewById(R.id.cYRB);
        clipsNRB = (RadioButton) findViewById(R.id.cNRB);
        lineYRB = (RadioButton) findViewById(R.id.hLYRB);
        lineNRB = (RadioButton) findViewById(R.id.hLNRB);


        //creating the arrays to hold the spinner objects
        final String[] gluedArr = {"Steps Glued?", "Yes", "No", "Over-Glued"};

        //adding the spinners to the array
        views[0] = stepsGluedSp;

        //setting the options to the spinners
        ArrayAdapter<String> adapter1 = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, gluedArr);
        stepsGluedSp.setAdapter(adapter1);

        //when the second size spinner is clicked
        stepsGluedSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View view, int arg2, long arg3) {
                int index = stepsGluedSp.getSelectedItemPosition();
                if (index != 0)
                    updateViewValidity(stepsGluedSp);
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                int index = stepsGluedSp.getSelectedItemPosition();
                if (index != 0)
                    updateViewValidity(stepsGluedSp);
            }
        });
    }

    //when the FAB is clicked
    public void fabClicked(View view){
        if(areViewsValid(views)) {
            //create a new intent (you do not get the current one because we do not need any
            // information from the home screen)
            Intent intent = new Intent(getApplicationContext(), Step_Rebuilding4.class);
            //this removes the animation
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            //extras--for passing data
            intent.putExtra("gluedIndex", stepsGluedSp.getSelectedItemPosition());
            //start activity
            startActivity(intent);

            boolean treeY, treeN, clipsY, clipsN, lineY, lineN;
            treeY = treeYRB.isChecked();
            treeN = treeNRB.isChecked();
            clipsY = clipsYRB.isChecked();
            clipsN = clipsNRB.isChecked();
            lineY = lineYRB.isChecked();
            lineN = lineNRB.isChecked();

        }else
            updateViewValidity(views);
    }

    public boolean onOptionsItemSelected(MenuItem item){
        startActivity(new Intent(Step_Rebuilding3.this, Step_Rebuilding2.class));
        return true;
    }
}
