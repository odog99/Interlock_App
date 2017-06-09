package com.oep.interlock_app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.oep.owenslaptop.interlock_app.R;

/*
 *By: Peter Lewis
 *Date: April 30, 2017
 */

public class Wall_Rebuilding2 extends AppCompatActivity {

    EditText heightInput;
    EditText lengthInput;
    CheckBox lineCheckBox;
    RadioButton straightRadioButton;
    RadioButton curvedRadioButton;
    View[] views = new View[2];

    private ListView mDrawerList;
    private ArrayAdapter<String> mAdapter;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private String mActivityTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wall_rebuilding_layout);
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
        //layout Views
        heightInput = (EditText) findViewById(R.id.height_input);
        lengthInput = (EditText) findViewById(R.id.length_input);
        lineCheckBox = (CheckBox) findViewById(R.id.hard_line_CheckBox);
        straightRadioButton = (RadioButton) findViewById(R.id.wallStraight_radioButton);
        curvedRadioButton = (RadioButton) findViewById(R.id.wallCurved_radioButton);

        views[0] = heightInput;
        views[1] = lengthInput;
        //EditText listeners
        heightInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                ViewValidity.removeOutline(v);
                return !ViewValidity.isViewValid(v);//keep up keyboard
            }
        });
        heightInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                ViewValidity.removeOutline(v);
            }
        });
        lengthInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                ViewValidity.removeOutline(v);
                return !ViewValidity.isViewValid(v);//keep up keyboard
            }
        });
        lengthInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                ViewValidity.removeOutline(v);
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

        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(position==0){
                    startActivity(new Intent(Wall_Rebuilding2.this, HomeScreen.class));
                }
                else if(position==1){
                    startActivity(new Intent(Wall_Rebuilding2.this, HelpPage.class));
                }
                else if(position==2){
                    startActivity(new Intent(Wall_Rebuilding2.this, EstimationPage.class));
                }
                else if(position==3){
                    startActivity(new Intent(Wall_Rebuilding2.this, DatabaseManagement.class));
                }
                else if(position==4){
                    startActivity(new Intent(Wall_Rebuilding2.this, EnterDatabaseIdActivity.class));
                }
                else if(position==5){//this will only be true if the user is owner
                    startActivity(new Intent(Wall_Rebuilding2.this, ActivityDatabaseAccounts.class));
                }
            }
        });
    }

    public void fabClicked(View view){
        if (ViewValidity.areViewsValid(views)) {
            //start activity (getIntent to save extras)
            Intent intent = getIntent();
            //update class
            intent.setClass(getApplicationContext(), Wall_Rebuilding3.class);
            //extras--for passing data
            intent.putExtra("heightInput", Double.parseDouble(heightInput.getText().toString()));
            intent.putExtra("lengthInput", Double.parseDouble(lengthInput.getText().toString()));
            intent.putExtra("lineChecked", lineCheckBox.isChecked());
            if(straightRadioButton.isChecked())
                intent.putExtra("straightCurvedNum", 0);
            else
                intent.putExtra("straightCurvedNum", 1);
            startActivity(intent);
        }else
            ViewValidity.updateViewValidity(views);
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
