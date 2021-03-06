package com.oep.interlock_app;

import android.app.Activity;
import android.content.Intent;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.MenuItem;

public class Interlock_Relaying_Complications extends AppCompatActivity {

    public static String sunkOut, paveSizeOut, weedOut, fillOut;
    private ListView mDrawerList;
    private ArrayAdapter<String> mAdapter;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private String mActivityTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interlock_relaying_complications);
        //setting up the back button
        try {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException npex) {
            try {
                getActionBar().setDisplayHomeAsUpEnabled(true);
            } catch (NullPointerException ex) {
                //back button not supported
            }
        }
        final SeekBar sunkAmtSlider = (SeekBar)findViewById(R.id.sunkAmtSlider);
        final SeekBar paverSlider = (SeekBar)findViewById(R.id.paverSlider);
        final FloatingActionButton nextBtn = (FloatingActionButton)findViewById(R.id.nextBtn);
        final TextView sunkAmtTxt = (TextView)findViewById(R.id.sunkDisplay);
        final TextView paversTxt = (TextView)findViewById(R.id.paversDisplay);
        final SeekBar jointFillSlider = (SeekBar)findViewById(R.id.jointFillSlider);
        final TextView jointFillTxt = (TextView)findViewById(R.id.jointFillDisplay);
        final TextView weedTxt = (TextView)findViewById(R.id.weedDisplay);
        final SeekBar weedSlider = (SeekBar)findViewById(R.id.weedSlider);

        jointFillSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(progress == 0){
                    jointFillTxt.setText("Not very hard");
                }else if(progress == 1){
                    jointFillTxt.setText("Somewhat hard");
                }else if(progress == 2){
                    jointFillTxt.setText("Fairly Hard");
                }else if(progress == 3){
                    jointFillTxt.setText("Moderately Hard");
                }else{
                    jointFillTxt.setText("Very hard");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        weedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(progress == 0){
                    weedTxt.setText("No weeds");
                }else if(progress == 1){
                    weedTxt.setText("Some weeds");
                }else if(progress == 2){
                    weedTxt.setText("Slightly weedy");
                }else if(progress == 3){
                    weedTxt.setText("Moderately weedy");
                }else{
                    weedTxt.setText("Very weedy");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        paverSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(progress == 0 ){
                    paversTxt.setText("Small Paver Size");
                }else if(progress == 1){
                    paversTxt.setText("Medium Paver Size");
                }else{
                    paversTxt.setText("Large Paver Size");
                }

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        sunkAmtSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress == 0) {
                    sunkAmtTxt.setText("Hasn't sunk");
                } else if (progress == 1) {
                    sunkAmtTxt.setText("Has sunk slightly");
                } else if (progress == 2) {
                    sunkAmtTxt.setText("Has sunk a moderate amount");
                } else if (progress == 3){
                    sunkAmtTxt.setText("Has sunk a great amount");
                } else if(progress == 4){
                    sunkAmtTxt.setText("Severely sunken");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    Intent nextPg = getIntent();
                    sunkOut = String.valueOf(sunkAmtTxt.getText());
                    paveSizeOut = String.valueOf(paversTxt.getText());
                    weedOut = String.valueOf(weedTxt.getText());
                    fillOut = String.valueOf(jointFillTxt.getText());
                    nextPg.setClass(getApplicationContext(), Interlock_Relaying_ToJob.class);
                    startActivity(nextPg);

            }
        });

        //code to use the drawer
        mDrawerList = (ListView)findViewById(R.id.navList);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mActivityTitle = getTitle().toString();

        addDrawerItems();
        setupDrawer();
        mDrawerToggle.setDrawerIndicatorEnabled(true);

        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        final Activity activity = this;

        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = null;
                if(position==0){
                    intent = new Intent(getApplicationContext(), HomeScreen.class);
                }
                else if(position==1){
                    intent = new Intent(getApplicationContext(), HelpPage.class);
                }
                else if(position==2){
                    intent = new Intent(getApplicationContext(), EstimationPage.class);
                }
                else if(position==3){
                    intent = new Intent(getApplicationContext(), DatabaseManagement.class);
                }
                else if(position==4){
                    intent = new Intent(getApplicationContext(), EnterDatabaseIdActivity.class);
                }
                else if(position==5){//this will only be true if the user is owner
                    intent = new Intent(getApplicationContext(), ActivityDatabaseAccounts.class);
                }
                ILDialog.showExitDialogWarning(activity, intent);
            }
        });

    }

    //called when the back button in the title bas is pressed
    public boolean onOptionsItemSelected(MenuItem item){
        if(mDrawerLayout.isDrawerOpen(Gravity.LEFT))
            mDrawerLayout.closeDrawer(Gravity.LEFT);
        else
            mDrawerLayout.openDrawer(Gravity.LEFT);
        return true;
    }

    private void addDrawerItems(){
        // Only have the "Database Permissions" if the user owns the database
        EstimationSheet estimationSheet = new EstimationSheet(EstimationSheet.ID_NOT_APPLICABLE, this);
        if(estimationSheet.isUserOwner()) {
            String[] osArray = {"Home Screen", "Help!",  "New Estimation", "Database Management", "Database Setup", "Database Permissions"};
            mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, osArray);
        } else {
            String[] osArray = { "Home Screen", "Help!",  "New Estimation", "Database Management", "Database Setup" };
            mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, osArray);
        }
        mDrawerList.setAdapter(mAdapter);
    }

    private void setupDrawer() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getSupportActionBar().setTitle("Navigation!");
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getSupportActionBar().setTitle(mActivityTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };
        mDrawerToggle.syncState();
    }
}
