package com.oep.interlock_app;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Created by: Peter Lewis
 * Date: 5/21/17
 */

class EstimationSheet {
    private GoogleAccountCredential googleAccountCredential;
    private ProgressDialog progressDialog;

    private static final int REQUEST_ACCOUNT_PICKER = 1000;
    private static final int REQUEST_AUTHORIZATION = 1001;
    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    private static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    static final int REQUEST_GET_DATABASE_ID = 1;

    private static final int NO_PROCESS = -1;
    private static final int GET_DATA_PROCESS = 0;
    private static final int GET_ESTIMATED_TIME_PROCESS = 1;
    private static final int ADD_ESTIMATION_PROCESS = 2;
    private static final int REMOVE_ESTIMATION_PROCESS = 3;
    private static final int SET_TIME_PROCESS = 4;
    private static final int CREATE_DATABASE_PROCESS = 5;
    private static final int ADD_PERMISSIONS_PROCESS = 6;
    private static final int REMOVE_PERMISSIONS_PROCESS = 7;
    private static final int GET_PERMISSIONS_PROCESS = 8;
    private static final int CHECK_DATABASE_ID_VALIDITY_PROCESS = 9;

    static final int COLUMN_ESTIMATION_ID = 0;
    static final int COLUMN_DATE = 1;
    static final int COLUMN_ESTIMATED_TIME = 2;
    static final int COLUMN_ACTUAL_TIME = 3;
    private static final int COLUMN_ARIA1 = 4;
    private static final int COLUMN_ARIA2 = 5;

    private int currentProcess = NO_PROCESS;
    private int currentHeldProcess = NO_PROCESS;

    private static final String DATABASE_TITLE = "Interlock App Database";

    static final String DATABASE_ID_FILE_NAME = "database_id";
    private static final String USER_TYPE_FILE_NAME = "user_type";

    static final String USER_TYPE_OWNER = "owner";
    private static final String USER_TYPE_EMPLOYEE = "employee";

    private static final int NO_ERROR = -1;
    private static final int UNKNOWN_ERROR = 0;
    static final int NO_GOOGLE_PLAY_SERVICES_ERROR = 1;

    private int sheetId;
    private String sheetName;
    private Activity activity;
    private Listener currentListener;
    private List<Object> currentEstimationData;
    private int currentEstimationId;
    private Double currentActualTime;
    private List<String> currentEmails;
    private boolean currentShowNoDataDialog;
    private boolean currentShowProgressDialog;
    private Permission currentPermission;
    private String currentDatabaseId;
    private Double currentEstimatedTime;
    private int nextEstimationId = 0;
    private List<List<Object>> pastEstimationData = null;

    private static final int OK_ACTION = 0;
    private static final int RETRY_ACTION = 1;

    static final int ID_NOT_APPLICABLE = -1;
    static final int WALL_REBUILDING_ID = 0;
    static final int CLEANING_SEALING_ID = 1;
    static final int STEP_REBUILDING_ID = 2;
    static final int JOINT_FILL_ID = 3;
    static final int INTERLOCK_RELAYING_ID = 4;

    private static final String WALL_REBUILDING_SHEET_NAME = "wall_rebuilding";
    private static final String CLEANING_SEALING_SHEET_NAME = "cleaning_sealing";
    private static final String STEP_REBUILDING_SHEET_NAME = "step_rebuilding";
    private static final String JOINT_FILL_SHEET_NAME = "joint_fill";
    private static final String INTERLOCK_RELAYING_SHEET_NAME = "interlock_relaying";
    private static final String WALL_REBUILDING_SHORT_FORM = "wr";
    private static final String CLEANING_SEALING_SHORT_FORM = "cs";
    private static final String STEP_REBUILDING_SHORT_FORM = "sr";
    private static final String JOINT_FILL_SHORT_FORM = "jf";
    private static final String INTERLOCK_RELAYING_SHORT_FORM = "ir";

    private static final String ACCOUNT_FILE_NAME = "accountName";
    private static final String[] SCOPES = { SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE_FILE};

    /**
     *
     * @param sheetId The ID of the Google Sheet. Use the ID's in this file (<SHEET_NAME>_ID)
     * @param activity The Activity that uses the sheet. Used for dialogs etc. You can usually use 'this'
     */

    EstimationSheet(int sheetId, Activity activity){
        this.sheetId = sheetId;
        this.activity = activity;

        // get the right sheet name
        switch (sheetId){
            case ID_NOT_APPLICABLE:
                sheetName = "";
                break;
            case WALL_REBUILDING_ID:
                sheetName = WALL_REBUILDING_SHEET_NAME;
                break;
            case CLEANING_SEALING_ID:
                sheetName = CLEANING_SEALING_SHEET_NAME;
                break;
            case STEP_REBUILDING_ID:
                sheetName = STEP_REBUILDING_SHEET_NAME;
                break;
            case JOINT_FILL_ID:
                sheetName = JOINT_FILL_SHEET_NAME;
                break;
            case INTERLOCK_RELAYING_ID:
                sheetName = INTERLOCK_RELAYING_SHEET_NAME;
                break;
            default:
                throw new IllegalArgumentException("'"+sheetId+"' is an invalid sheet ID. Use one of the given IDs with 'EstimationSheet.<SHEET_NAME>_ID'.");
        }



        //set up progress dialog
        progressDialog = new ProgressDialog(activity);
        progressDialog.setCanceledOnTouchOutside(false);

        // Initialize credentials and sheetsService object.
        googleAccountCredential = GoogleAccountCredential.usingOAuth2(
                this.activity.getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }


    /**
     * Initialize an instance of this class using a pre defined dialog.
     * @param sheetId the id of the sheet use one of the final integer id in this class
     * @param activity the activity to use often you can just have 'this'
     * @param progressDialog the progress dialog that should be used when processing
     */
    EstimationSheet(int sheetId, Activity activity, ProgressDialog progressDialog){
        this.sheetId = sheetId;
        this.activity = activity;

        // find the right sheet name
        switch (sheetId){
            case ID_NOT_APPLICABLE:
                sheetName = "";
                break;
            case WALL_REBUILDING_ID:
                sheetName = WALL_REBUILDING_SHEET_NAME;
                break;
            case CLEANING_SEALING_ID:
                sheetName = CLEANING_SEALING_SHEET_NAME;
                break;
            case STEP_REBUILDING_ID:
                sheetName = STEP_REBUILDING_SHEET_NAME;
                break;
            case JOINT_FILL_ID:
                sheetName = JOINT_FILL_SHEET_NAME;
                break;
            case INTERLOCK_RELAYING_ID:
                sheetName = INTERLOCK_RELAYING_SHEET_NAME;
                break;
            default:
                throw new IllegalArgumentException("'"+sheetId+"' is an invalid sheet ID. Use one of the given IDs with 'EstimationSheet.<SHEET_NAME>_ID'.");
        }



        //set up progress dialog
        this.progressDialog = progressDialog;

        // Initialize credentials and sheetsService object.
        googleAccountCredential = GoogleAccountCredential.usingOAuth2(
                this.activity.getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }

    /**
     * Creates a new database
     * @param listener the CreateDatabaseListener that is notified when task is finished.
     */

    void startCreatingDatabase(final CreateDatabaseListener listener){
        // do not run multiple processes at once since the listener needs to be stored as a
        // global variable for when the account is chosen. If you where to run multiple processes at
        // once, only the newest listener would be used and it would be used for both processes.
        if(currentProcess != NO_PROCESS && currentProcess != CREATE_DATABASE_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        // update variables. These are used to recall this process after an account has been chosen.
        currentProcess = CREATE_DATABASE_PROCESS;
        currentListener = listener;

        if (! isGooglePlayServicesAvailable()){
            currentProcess = NO_PROCESS;
            acquireGooglePlayServices();
            listener.whenFinished(false, NO_GOOGLE_PLAY_SERVICES_ERROR);
        }

        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }

        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to create a database.", RETRY_ACTION);
        }

        else{
            // start the process
            TaskCreateDatabase taskCreateDatabase = new TaskCreateDatabase(new CreateDatabaseListener() {
                @Override
                public void whenFinished(boolean success, int errorId) {
                    currentProcess = NO_PROCESS;
                    listener.whenFinished(success, errorId);
                }
            });
            taskCreateDatabase.execute();
        }
    }


    void startAddingPermissions(final List<String> emails, final AddPermissionsListener listener){
        // do not run multiple processes at once since the listener needs to be stored as a
        // global variable for when the account is chosen. If you where to run multiple processes at
        // once, only the newest listener would be used and it would be used for both processes.
        if(currentProcess != NO_PROCESS && currentProcess != ADD_PERMISSIONS_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        // update variables. These are used to recall this process after an account has been chosen.
        currentProcess = ADD_PERMISSIONS_PROCESS;
        currentEmails = emails;
        currentListener = listener;
        if (! isGooglePlayServicesAvailable()){
            currentProcess = NO_PROCESS;
            acquireGooglePlayServices();
            listener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to give Google accounts access to the database.", RETRY_ACTION);
        }
        else{
            //start the process
            TaskAddPermissions taskAddPermissions = new TaskAddPermissions(emails, new AddPermissionsListener() {
                @Override
                public void whenFinished(boolean success) {
                    currentProcess = NO_PROCESS;
                    listener.whenFinished(success);
                }
            });
            taskAddPermissions.execute();
        }
    }


    void startRemovingPermission(final Permission permission, final RemovePermissionListener listener){
        // do not run multiple processes at once since the listener needs to be stored as a
        // global variable for when the account is chosen. If you where to run multiple processes at
        // once, only the newest listener would be used and it would be used for both processes.
        if(currentProcess != NO_PROCESS && currentProcess != REMOVE_PERMISSIONS_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        // update variables. These are used to recall this process after an account has been chosen.
        currentProcess = REMOVE_PERMISSIONS_PROCESS;
        currentPermission = permission;
        currentListener = listener;
        if (! isGooglePlayServicesAvailable()){
            currentProcess = NO_PROCESS;
            acquireGooglePlayServices();
            listener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to remove account access to the database.", RETRY_ACTION);
        }
        else{
            //start the process
            TaskRemovePermission taskRemovePermission = new TaskRemovePermission(permission, new RemovePermissionListener() {
                @Override
                public void whenFinished(boolean success) {
                    currentProcess = NO_PROCESS;
                    listener.whenFinished(success);
                }
            });
            taskRemovePermission.execute();
        }
    }


    void startGettingPermissions(final GetPermissionsListener listener){
        // do not run multiple processes at once since the listener needs to be stored as a
        // global variable for when the account is chosen. If you where to run multiple processes at
        // once, only the newest listener would be used and it would be used for both processes.
        if(currentProcess != NO_PROCESS && currentProcess != GET_PERMISSIONS_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        // update variables. These are used to recall this process after an account has been chosen.
        currentProcess = GET_PERMISSIONS_PROCESS;
        currentListener = listener;

        if (! isGooglePlayServicesAvailable()){
            currentProcess = NO_PROCESS;
            acquireGooglePlayServices();
            listener.whenFinished(false, null);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to get the accounts with permission to access the database.", RETRY_ACTION);
        }
        else{
            // start the process
            TaskGetPermissions taskGetPermissions = new TaskGetPermissions(new GetPermissionsListener() {
                @Override
                public void whenFinished(boolean success, List<Permission> permissions) {
                    currentProcess = NO_PROCESS;
                    listener.whenFinished(success, permissions);
                }
            });
            taskGetPermissions.execute();
        }
    }

    void startCheckingDatabaseIdValidity(final String databaseId, final CheckDatabaseIdValidityListener listener){
        // do not run multiple processes at once since the listener needs to be stored as a
        // global variable for when the account is chosen. If you where to run multiple processes at
        // once, only the newest listener would be used and it would be used for both processes.
        if(currentProcess != NO_PROCESS && currentProcess != CHECK_DATABASE_ID_VALIDITY_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        // update variables. These are used to recall this process after an account has been chosen.
        currentProcess = CHECK_DATABASE_ID_VALIDITY_PROCESS;
        currentListener = listener;
        currentDatabaseId = databaseId;

        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            currentProcess = NO_PROCESS;
            listener.whenFinished(false, NO_GOOGLE_PLAY_SERVICES_ERROR);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection save the database ID.", RETRY_ACTION);
        }
        else{
            // start the process
            TaskCheckDatabaseIdValidity taskCheckDatabaseIdValidity = new TaskCheckDatabaseIdValidity(databaseId, new CheckDatabaseIdValidityListener() {
                @Override
                public void whenFinished(boolean validId, int errorId) {
                    currentProcess = NO_PROCESS;
                    listener.whenFinished(validId, errorId);
                }
            });
            taskCheckDatabaseIdValidity.execute();
        }
    }

    /**
     *
     * @param newData the data to make estimation with (was just collected from user)
     * @param estimationListener GetDataListener for what to do when finished getting estimation
     */
    void startEstimation(final List<Object> newData, final EstimationListener estimationListener){
        // do not run multiple processes at once since the listener needs to be stored as a
        // global variable for when the account is chosen. If you where to run multiple processes at
        // once, only the newest listener would be used and it would be used for both processes.
        if(currentProcess != NO_PROCESS && currentProcess != GET_ESTIMATED_TIME_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        try {
            // get the data first to make estimation
            currentHeldProcess = GET_ESTIMATED_TIME_PROCESS;
            GetDataListener getDataGetDataListener = new GetDataListener() {
                @Override
                public void whenFinished(boolean success, List<List<Object>> dataFromSheets) {
                    //finished getting data from Sheets
                    currentProcess = GET_ESTIMATED_TIME_PROCESS;
                    currentHeldProcess = NO_PROCESS;
                    if (success) {
                        // make estimation
                        EstimationListener estimationListener1 = new EstimationListener() {
                            @Override
                            public void whenFinished(boolean success, boolean accurate, Double estimatedHours) {
                                currentProcess = NO_PROCESS;
                                progressDialog.dismiss();
                                estimationListener.whenFinished(success, accurate, estimatedHours);
                                if(success)
                                    currentEstimatedTime = estimatedHours;
                                else
                                    currentEstimatedTime = -1.0;
                            }
                        };
                        currentListener = estimationListener1;
                        new TaskGetEstimation(newData, dataFromSheets, estimationListener1).execute();
                    } else {
                        //abort startEstimation; an error has occurred
                        progressDialog.dismiss();
                        currentProcess = NO_PROCESS;
                        currentEstimatedTime = -1.0;
                        estimationListener.whenFinished(false, false, null);
                    }
                }
            };
            currentListener = getDataGetDataListener;
            try{
                getData(true, true, getDataGetDataListener);
            } catch (Exception e){
                e.printStackTrace();
            }
        }catch(Exception e){
            e.printStackTrace();
            currentProcess = NO_PROCESS;
            currentHeldProcess = NO_PROCESS;
        }
    }

    /**
     * Adds an estimation to the Google Sheet.
     * @param data The data which was collected for the estimation
     */
    void startAddingEstimation(List<Object> data,  final AddEstimationListener addEstimationListener){
        // do not run multiple processes at once since the listener needs to be stored as a
        // global variable for when the account is chosen. If you where to run multiple processes at
        // once, only the newest listener would be used and it would be used for both processes.
        if(currentProcess != NO_PROCESS && currentProcess != ADD_ESTIMATION_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");

        // update variables. These are used to recall this process after an account has been chosen.
        currentProcess = ADD_ESTIMATION_PROCESS;

        currentEstimationData = data;
        currentListener = addEstimationListener;

        if (! isGooglePlayServicesAvailable()){
            currentProcess = NO_PROCESS;
            acquireGooglePlayServices();
            addEstimationListener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to save data.", RETRY_ACTION);
        }
        else{

            // Add estimation id, date, estimated time, and empty space (for where the actual time will be) to data
            data.add(0, nextEstimationId);
            data.add(1,new SimpleDateFormat("MM/dd/yyyy").format(Calendar.getInstance(
                    TimeZone.getTimeZone("America/Montreal")).getTime()));
            data.add(2, currentEstimatedTime);
            data.add(3, "");

            progressDialog.setMessage("Storing data...");
            List<List<Object>> preparedData = new ArrayList<>();
            preparedData.add(data);

            // start process
            TaskAddEstimation taskAddEstimation = new TaskAddEstimation(preparedData, new AddEstimationListener() {
                @Override
                public void whenFinished(boolean success) {
                    currentProcess = NO_PROCESS;
                    progressDialog.dismiss();
                    addEstimationListener.whenFinished(success);
                }
            });
            taskAddEstimation.execute();
        }
    }

    /**
     * Removes the given estimation from the Google Sheet.
     * @param estimationId the ID of the estimation to remove
     */
    void startRemovingEstimation(final int estimationId, final RemoveEstimationListener removeEstimationListener){
        if(pastEstimationData == null) {
            //get data first to get the row number that needs to be removed
            GetDataListener getDataListener = new GetDataListener() {
                @Override
                public void whenFinished(boolean success, List<List<Object>> output) {
                    removeEstimation(estimationId, output, removeEstimationListener);
                }
            };
            currentListener = getDataListener;
            getData(false, true, getDataListener);
        } else{
            removeEstimation(estimationId, pastEstimationData, removeEstimationListener);
        }
    }

    /**
     * called only from startRemoving estimation
     */

    private void removeEstimation(final int estimationId, List<List<Object>> data, final RemoveEstimationListener removeEstimationListener){
        // do not run multiple processes at once since the listener needs to be stored as a
        // global variable for when the account is chosen. If you where to run multiple processes at
        // once, only the newest listener would be used and it would be used for both processes.
        if(currentProcess != NO_PROCESS && currentProcess != REMOVE_ESTIMATION_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = REMOVE_ESTIMATION_PROCESS;
        currentEstimationId = estimationId;
        pastEstimationData = data;
        currentListener = removeEstimationListener;
        if (!isGooglePlayServicesAvailable()) {
            currentProcess = NO_PROCESS;
            acquireGooglePlayServices();
            removeEstimationListener.whenFinished(false);
        } else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to remove data from the database.", RETRY_ACTION);
        } else {

            TaskRemoveEstimation taskRemoveEstimation = new TaskRemoveEstimation(estimationId, data, new RemoveEstimationListener() {
                @Override
                public void whenFinished(boolean success) {
                    currentProcess = NO_PROCESS;
                    removeEstimationListener.whenFinished(success);
                }
            });
            taskRemoveEstimation.execute();
        }
    }

    /**
     * Sets the actual time taken for a job
     * @param estimationId Id of the estimation that is to have the time changed.
     * @param totalHours How long the job took in hours.
     */
    void startSettingActualTime(final int estimationId, final double totalHours, final SetActualTimeListener setActualTimeListener){
        if(pastEstimationData == null) {
            //get data first to find the row number that needs to be updated
            GetDataListener getDataListener = new GetDataListener() {
                @Override
                public void whenFinished(boolean success, List<List<Object>> output) {
                    setActualTime(estimationId, totalHours, output, setActualTimeListener);
                }
            };
            currentListener = getDataListener;
            getData(false, true, getDataListener);
        } else{
            setActualTime(estimationId, totalHours, pastEstimationData, setActualTimeListener);
        }
    }

    /**
     * called only from startSettingActualTime
     */

    private void setActualTime(int estimationId, double totalHours, List<List<Object>> data, final SetActualTimeListener setActualTimeListener){
        // do not run multiple processes at once since the listener needs to be stored as a
        // global variable for when the account is chosen. If you where to run multiple processes at
        // once, only the newest listener would be used and it would be used for both processes.
        if(currentProcess != NO_PROCESS && currentProcess != SET_TIME_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = SET_TIME_PROCESS;
        currentEstimationId = estimationId;
        currentActualTime = totalHours;
        currentListener = setActualTimeListener;
        if (! isGooglePlayServicesAvailable()){
            currentProcess = NO_PROCESS;
            acquireGooglePlayServices();
            setActualTimeListener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to save the actual time a job took.", RETRY_ACTION);
        }
        else{
            TaskSetActualTime taskSetActualTime = new TaskSetActualTime(totalHours, estimationId, data, new SetActualTimeListener() {
                @Override
                public void whenFinished(boolean success) {
                    currentProcess = NO_PROCESS;
                    setActualTimeListener.whenFinished(success);
                }
            });
            taskSetActualTime.execute();
        }
    }

    /**
     * Hides the progress dialog when finished getting data
     * @param getDataListener for when finished
     */

    void startGettingAllData(final GetDataListener getDataListener){
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setCanceledOnTouchOutside(false);
        //get all data from one sheet after another
        new EstimationSheet(WALL_REBUILDING_ID, activity, pd).getData(false, true, new GetDataListener() {
            @Override
            public void whenFinished(final boolean WRSuccess, final List<List<Object>> WROutput) {
                new EstimationSheet(CLEANING_SEALING_ID, activity, pd).getData(false, false, new GetDataListener() {
                    @Override
                    public void whenFinished(final boolean CSSuccess, final List<List<Object>> CSOutput) {
                        new EstimationSheet(STEP_REBUILDING_ID, activity, pd).getData(false, false, new GetDataListener() {
                            @Override
                            public void whenFinished(final boolean SRSuccess, final List<List<Object>> SROutput) {
                                new EstimationSheet(JOINT_FILL_ID, activity, pd).getData(false, false, new GetDataListener() {
                                    @Override
                                    public void whenFinished(final boolean JFSuccess, final List<List<Object>> JFOutput) {
                                        new EstimationSheet(INTERLOCK_RELAYING_ID, activity, pd).getData(false, false, new GetDataListener() {
                                            @Override
                                            public void whenFinished(final boolean IRSuccess, final List<List<Object>> IROutput) {
                                                List<List<Object>> output = new ArrayList<>();
                                                // for each sheet that had success, set the full
                                                // estimation id and add every estimation the final
                                                // output
                                                if(WRSuccess){
                                                    for(List<Object> estimation : WROutput){
                                                        estimation.set(COLUMN_ESTIMATION_ID, WALL_REBUILDING_SHORT_FORM+estimation.get(COLUMN_ESTIMATION_ID));
                                                        output.add(estimation);
                                                    }
                                                }
                                                if(CSSuccess){
                                                    for(List<Object> estimation : CSOutput){
                                                        estimation.set(COLUMN_ESTIMATION_ID, CLEANING_SEALING_SHORT_FORM+estimation.get(COLUMN_ESTIMATION_ID));
                                                        output.add(estimation);
                                                    }
                                                }
                                                if(SRSuccess){
                                                    for(List<Object> estimation : SROutput){
                                                        estimation.set(COLUMN_ESTIMATION_ID, STEP_REBUILDING_SHORT_FORM+estimation.get(COLUMN_ESTIMATION_ID));
                                                        output.add(estimation);
                                                    }
                                                }
                                                if(JFSuccess){
                                                    for(List<Object> estimation : JFOutput){
                                                        estimation.set(COLUMN_ESTIMATION_ID, JOINT_FILL_SHORT_FORM+estimation.get(COLUMN_ESTIMATION_ID));
                                                        output.add(estimation);
                                                    }
                                                }
                                                if(IRSuccess){
                                                    for(List<Object> estimation : IROutput){
                                                        estimation.set(COLUMN_ESTIMATION_ID, INTERLOCK_RELAYING_SHORT_FORM+estimation.get(COLUMN_ESTIMATION_ID));
                                                        output.add(estimation);
                                                    }
                                                }
                                                pd.dismiss();
                                                // success if any of the sheets where successful
                                                getDataListener.whenFinished((WRSuccess || CSSuccess || SRSuccess || JFSuccess || IRSuccess), output);
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    static int getSheetIdFromFullEstimationId(String fullEstimationId){
        if(fullEstimationId.contains(WALL_REBUILDING_SHORT_FORM)){
            return WALL_REBUILDING_ID;
        }else if (fullEstimationId.contains(CLEANING_SEALING_SHORT_FORM)){
            return CLEANING_SEALING_ID;
        }else if (fullEstimationId.contains(STEP_REBUILDING_SHORT_FORM)){
            return STEP_REBUILDING_ID;
        }else if (fullEstimationId.contains(JOINT_FILL_SHORT_FORM)){
            return JOINT_FILL_ID;
        }else if (fullEstimationId.contains(INTERLOCK_RELAYING_SHORT_FORM)){
            return INTERLOCK_RELAYING_ID;
        }else{
            return ID_NOT_APPLICABLE;
        }
    }

    static String getProperSheetNameFromFullEstimationId(String fullEstimationId){
        if(fullEstimationId.contains(WALL_REBUILDING_SHORT_FORM)){
            return "Wall Rebuilding";
        }else if (fullEstimationId.contains(CLEANING_SEALING_SHORT_FORM)){
            return "Cleaning and Sealing";
        }else if (fullEstimationId.contains(STEP_REBUILDING_SHORT_FORM)){
            return "Step Rebuilding";
        }else if (fullEstimationId.contains(JOINT_FILL_SHORT_FORM)){
            return "Joint Fill";
        }else if (fullEstimationId.contains(INTERLOCK_RELAYING_SHORT_FORM)){
            return "Interlock Relaying";
        }else{
            return "";
        }
    }

    static int getSmallEstimationIdFromFullEstimationId(String fullEstimationId){
        if(fullEstimationId.contains(WALL_REBUILDING_SHORT_FORM)){
            return Integer.parseInt(fullEstimationId.replaceFirst(WALL_REBUILDING_SHORT_FORM, ""));
        }else if (fullEstimationId.contains(CLEANING_SEALING_SHORT_FORM)){
            return Integer.parseInt(fullEstimationId.replaceFirst(CLEANING_SEALING_SHORT_FORM, ""));
        }else if (fullEstimationId.contains(STEP_REBUILDING_SHORT_FORM)){
            return Integer.parseInt(fullEstimationId.replaceFirst(STEP_REBUILDING_SHORT_FORM, ""));
        }else if (fullEstimationId.contains(JOINT_FILL_SHORT_FORM)){
            return Integer.parseInt(fullEstimationId.replaceFirst(JOINT_FILL_SHORT_FORM, ""));
        }else if (fullEstimationId.contains(INTERLOCK_RELAYING_SHORT_FORM)){
            return Integer.parseInt(fullEstimationId.replaceFirst(INTERLOCK_RELAYING_SHORT_FORM, ""));
        }else{
            return -1;
        }
    }

    boolean doesUserHaveRole(){
        return getUserType() != null;
    }

    boolean isDatabaseIdSaved(){
        return getDatabaseId() != null;
    }

    boolean isUserOwner(){
        String userType = getUserType();
        if (userType == null || userType.equals(""))
            throw new NullPointerException("There is either no user type file, or there is no data in it.");
        else if(userType.equals(USER_TYPE_OWNER))
            return true;
        else if (userType.equals(USER_TYPE_EMPLOYEE))
            return false;
        else
            throw new NullPointerException("The data '"+userType+"'  which is in the user type file is not valid.");
    }

    void setUserType(String userType){System.err.println("user type being set to "+userType);
        if(!(userType.equals(USER_TYPE_OWNER) || userType.equals(USER_TYPE_EMPLOYEE)))
            throw new IllegalArgumentException("Cannot save userType '"+userType+"'. Invalid userType.");
        else{
            try {
                FileOutputStream fos = activity.openFileOutput(USER_TYPE_FILE_NAME, Context.MODE_PRIVATE);
                fos.write(userType.getBytes());
                fos.close();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private String getDatabaseId(){
        try {
            FileInputStream input = activity.openFileInput(DATABASE_ID_FILE_NAME);
            InputStreamReader inputStreamReader = new InputStreamReader(input);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String id =  bufferedReader.readLine();
            input.close();
            inputStreamReader.close();
            bufferedReader.close();
            return id;
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private String getUserType(){
        try {
            FileInputStream input = activity.openFileInput(USER_TYPE_FILE_NAME);
            InputStreamReader inputStreamReader = new InputStreamReader(input);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String userType =  bufferedReader.readLine();
            input.close();
            inputStreamReader.close();
            bufferedReader.close();
            if (userType.equals(USER_TYPE_OWNER) || userType.equals(USER_TYPE_EMPLOYEE))
                return userType;
            else
                return null;
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private int getEstimationRowNum(int estimationId, List<List<Object>> data){
        //search through data with a binary search
        if (data == null)
            return -1;
        int low = 0;
        int high = data.size() - 1;
        int n = 0;
        int location = -1;
        while (low <= high){
            n ++;
            int mid = (low + high)/2;
            location = -1;
            int midId = Integer.parseInt((String) data.get(mid).get(COLUMN_ESTIMATION_ID));
            if (estimationId == midId){
                location = mid;
                break;
            } else if (estimationId < midId)
                high = mid - 1;
            else
                low = mid + 1;
        }
        return location + 1; // Row numbers start at 1
    }

    private boolean toBoolean(String str){
        if(str.toLowerCase().equals("true"))
            return true;
        else if (str.toLowerCase().equals("false"))
            return false;
        else
            throw new IllegalArgumentException("'"+str+"' cannot be converted to a boolean.");
    }

    private void showErrorDialog(String error, int action){
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Error");
        builder.setMessage(error);
        if(action == OK_ACTION)
            builder.setPositiveButton("OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
        else if(action == RETRY_ACTION) {
            builder.setPositiveButton("RETRY",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            resumeProcess();
                        }
                    });
            builder.setNegativeButton("CANCEL",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            currentProcess = NO_PROCESS;
                            dialog.dismiss();
                        }
                    });
        }
        else
            throw new IllegalArgumentException("'"+action+"' is an invalid action. Please use one of the given actions which are in the format <ACTION>_ACTION.");

        builder.create().show();
    }

    private void showDialog(String message, String title, int action){
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setMessage(message);
        if(action == OK_ACTION)
            builder.setPositiveButton("OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
        else if(action == RETRY_ACTION) {
            builder.setPositiveButton("RETRY",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            resumeProcess();
                        }
                    });
            builder.setNegativeButton("CANCEL",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            currentProcess = NO_PROCESS;
                            dialog.dismiss();
                        }
                    });
        }
        else
            throw new IllegalArgumentException("'"+action+"' is an invalid action. Please use one of the given actions which are in the format <ACTION>_ACTION.");

        builder.create().show();
    }

    private void resumeProcess(){
        switch (currentProcess) {
            case GET_DATA_PROCESS:
                getData(currentShowNoDataDialog, currentShowProgressDialog, (GetDataListener) currentListener);
                break;
            case GET_ESTIMATED_TIME_PROCESS:
                getData(currentShowNoDataDialog, currentShowProgressDialog, (GetDataListener) currentListener);
                break;
            case ADD_ESTIMATION_PROCESS:
                startAddingEstimation(currentEstimationData, (AddEstimationListener) currentListener);
                break;
            case REMOVE_ESTIMATION_PROCESS:
                startRemovingEstimation(currentEstimationId, (RemoveEstimationListener) currentListener);
                break;
            case SET_TIME_PROCESS:
                startSettingActualTime(currentEstimationId, currentActualTime, (SetActualTimeListener) currentListener);
                break;
            case CREATE_DATABASE_PROCESS:
                startCreatingDatabase((CreateDatabaseListener) currentListener);
                break;
            case ADD_PERMISSIONS_PROCESS:
                startAddingPermissions(currentEmails, (AddPermissionsListener) currentListener);
                break;
            case REMOVE_PERMISSIONS_PROCESS:
                startRemovingPermission(currentPermission, (RemovePermissionListener) currentListener);
                break;
            case CHECK_DATABASE_ID_VALIDITY_PROCESS:
                startCheckingDatabaseIdValidity(currentDatabaseId, (CheckDatabaseIdValidityListener) currentListener);
                break;
            case GET_PERMISSIONS_PROCESS:
                startGettingPermissions((GetPermissionsListener) currentListener);
                break;
        }
    }

    /**
     * Does not hide progress dialog after finished
     * @param getDataListener for when process is finished
     */
    private void getData(boolean showNoDataDialog, boolean showProgressDialog, final GetDataListener getDataListener) {
        if(currentProcess != NO_PROCESS && currentProcess != GET_DATA_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = GET_DATA_PROCESS;
        currentListener = getDataListener;
        currentShowNoDataDialog = showNoDataDialog;
        currentShowProgressDialog = showProgressDialog;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            getDataListener.whenFinished(false, null);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to get an data from the database.", RETRY_ACTION);
        }
        else{
            TaskGetData taskGetData = new TaskGetData(showNoDataDialog, showProgressDialog, new GetDataListener() {
                @Override
                public void whenFinished(boolean success, List<List<Object>> output) {
                    currentProcess = NO_PROCESS;
                    if(output != null) {
                        pastEstimationData = output;
                        nextEstimationId = getNextEstimationId(output);
                    }
                    getDataListener.whenFinished(success, output);
                }
            });
            taskGetData.execute();
        }
    }

    private int getNextEstimationId(List<List<Object>> data){
        int maxId = -1;
        try {
            // Try to get the last estimation and from that the estimation ID
            maxId = Integer.parseInt((String) data.get(data.size() - 1).get(COLUMN_ESTIMATION_ID));
        } catch (Exception ignore){}
        return maxId + 1;
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                activity, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = null;
            try {
                FileInputStream input = activity.openFileInput(ACCOUNT_FILE_NAME);
                InputStreamReader inputStreamReader = new InputStreamReader(input);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                accountName = bufferedReader.readLine();
                input.close();
                inputStreamReader.close();
                bufferedReader.close();
            } catch (Exception ignored){}
            if (accountName != null) {
                googleAccountCredential.setSelectedAccountName(accountName);
                resumeProcess();
            } else {
                // Start a dialog from which the user can choose an account
                activity.startActivityForResult(
                        googleAccountCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    activity,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != Activity.RESULT_OK) {
                    showErrorDialog(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.", OK_ACTION);
                } else {
                    resumeProcess();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == Activity.RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        try {
                            FileOutputStream fos = activity.openFileOutput(ACCOUNT_FILE_NAME, Context.MODE_PRIVATE);
                            fos.write(accountName.getBytes());
                            fos.close();
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                        googleAccountCredential.setSelectedAccountName(accountName);
                        resumeProcess();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK) {
                    resumeProcess();
                }
                break;
        }
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(activity);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(activity);
        if (apiAvailability.isUserResolvableError(connectionStatusCode))
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    private void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                activity,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }


    private class TaskCreateDatabase extends AsyncTask<Void, Void, Void> {

        private com.google.api.services.sheets.v4.Sheets sheetsService = null;
        com.google.api.services.drive.Drive driveService = null;
        private Exception lastError = null;
        private CreateDatabaseListener createDatabaseListener = null;

        TaskCreateDatabase(CreateDatabaseListener createDatabaseListener) {
            this.createDatabaseListener = createDatabaseListener;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            sheetsService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
            driveService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            boolean canceled = false;
            String databaseId = "";
            try {
                databaseId = createDatabase();
            } catch (Exception e) {
                canceled = true;
                lastError = e;
                cancel(true);
            }
            if(!canceled)
                saveDatabaseId(databaseId);
            return null;
        }


        private String createDatabase() throws IOException {
            // Create Sheets
            List<Sheet> sheets = new ArrayList<>();
            // Wall rebuilding
            SheetProperties wallRebuildingProperties = new SheetProperties();
            wallRebuildingProperties.setTitle(WALL_REBUILDING_SHEET_NAME);
            Sheet wallRebuildingSheet = new Sheet();
            wallRebuildingSheet.setProperties(wallRebuildingProperties);
            sheets.add(wallRebuildingSheet);
            // Cleaning sealing
            SheetProperties cleaningSealingProperties = new SheetProperties();
            cleaningSealingProperties.setTitle(CLEANING_SEALING_SHEET_NAME);
            Sheet cleaningSealingSheet = new Sheet();
            cleaningSealingSheet.setProperties(cleaningSealingProperties);
            sheets.add(cleaningSealingSheet);
            // Step rebuilding
            SheetProperties stepRebuildingProperties = new SheetProperties();
            stepRebuildingProperties.setTitle(STEP_REBUILDING_SHEET_NAME);
            Sheet stepRuibuildingSheet = new Sheet();
            stepRuibuildingSheet.setProperties(stepRebuildingProperties);
            sheets.add(stepRuibuildingSheet);
            // Joint fill
            SheetProperties jointFillProperties = new SheetProperties();
            jointFillProperties.setTitle(JOINT_FILL_SHEET_NAME);
            Sheet jointFillSheet = new Sheet();
            jointFillSheet.setProperties(jointFillProperties);
            sheets.add(jointFillSheet);
            // Interlock relaying
            SheetProperties interlockRelayingProperties = new SheetProperties();
            interlockRelayingProperties.setTitle(INTERLOCK_RELAYING_SHEET_NAME);
            Sheet interlockRelayingSheet = new Sheet();
            interlockRelayingSheet.setProperties(interlockRelayingProperties);
            sheets.add(interlockRelayingSheet);

            // Setup properties
            SpreadsheetProperties spreadsheetProperties = new SpreadsheetProperties();
            spreadsheetProperties.setTitle(DATABASE_TITLE);

            // Setup request body
            Spreadsheet requestBody = new Spreadsheet();
            requestBody.setProperties(spreadsheetProperties);
            requestBody.setSheets(sheets);

            // Create Spreadsheet
            return sheetsService.spreadsheets().create(requestBody).execute().getSpreadsheetId();
        }


        private void saveDatabaseId(String databaseId){
            try {
                FileOutputStream fos = activity.openFileOutput(DATABASE_ID_FILE_NAME, Context.MODE_PRIVATE);
                fos.write(databaseId.getBytes());
                fos.close();
            } catch (Exception e){
                e.printStackTrace();
            }
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Setting up database...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void output) {
            progressDialog.dismiss();
            createDatabaseListener.whenFinished(true, NO_ERROR);
        }

        @Override
        protected void onCancelled() {
            progressDialog.dismiss();
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                    createDatabaseListener.whenFinished(false, NO_GOOGLE_PLAY_SERVICES_ERROR);
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    lastError.printStackTrace();
                    createDatabaseListener.whenFinished(false, UNKNOWN_ERROR);
                }
            } else {
                System.err.println("Request to create Spreadsheet canceled.");
                createDatabaseListener.whenFinished(false, UNKNOWN_ERROR);
            }
        }
    }


    private class TaskAddPermissions extends AsyncTask<Void, Void, Void> {

        private AddPermissionsListener listener = null;
        private Exception lastError = null;
        com.google.api.services.drive.Drive driveService = null;
        private List<String> emails = null;

        TaskAddPermissions(List<String> emails, AddPermissionsListener listener) {
            this.listener = listener;
            this.emails = emails;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            driveService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            setPermissions();
            return null;
        }


        private void setPermissions() {
            String fileId = getDatabaseId();
            for(String email : emails) {
                try {
                    Permission permission = new Permission();
                    permission.setType("user");
                    permission.setRole("writer");
                    permission.setValue(email);
                    permission.setEmailAddress(email);
                    // Email notification message
                    String emailMessage =
                            "Dear Interlock App user,\n" +
                                    "you have been given access to an Interlock App database from " +
                                    googleAccountCredential.getSelectedAccountName() + ". Please do not " +
                                    "edit the database manually, as this could cause problems. To set up the " +
                                    "database with your Interlock App, open the application and enter the " +
                                    "identification number\n" +
                                    getDatabaseId() + "\n" +
                                    "where requested.";
                    /*driveService.permissions().create(fileId, permission).setSendNotificationEmail(true)
                            .setEmailMessage(emailMessage).execute();*/
                    driveService.permissions().insert(fileId, permission)
                            .setSendNotificationEmails(true).setEmailMessage(emailMessage).execute();
                } catch (IOException e){
                    lastError = e;
                }
                System.out.println("Email: "+email);
            }

            if(lastError != null)
                cancel(true);
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Giving accounts access to database...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void output) {
            progressDialog.dismiss();
            listener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    lastError.printStackTrace();
                }
            } else {
                System.err.println("Request to get add permissions canceled.");
            }
            progressDialog.dismiss();
            listener.whenFinished(false);
        }
    }


    private class TaskRemovePermission extends AsyncTask<Void, Void, Void> {

        private RemovePermissionListener listener = null;
        private Exception lastError = null;
        com.google.api.services.drive.Drive driveService = null;
        private Permission permission = null;

        TaskRemovePermission(Permission permission, RemovePermissionListener listener) {
            this.listener = listener;
            this.permission = permission;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            driveService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            try {
                removePermissions();
            } catch (Exception e) {
                lastError = e;
                cancel(true);
            }
            return null;
        }


        private void removePermissions() throws IOException {
            driveService.permissions().delete(getDatabaseId(), permission.getId()).execute();
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Removing database access for accounts...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void output) {
            progressDialog.dismiss();
            listener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            progressDialog.dismiss();
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    lastError.printStackTrace();
                }
            } else {
                System.err.println("Request to remove permissions canceled.");
            }
            listener.whenFinished(false);
        }
    }


    private class TaskGetPermissions extends AsyncTask<Void, Void, List<Permission>> {

        private GetPermissionsListener listener = null;
        private Exception lastError = null;
        com.google.api.services.drive.Drive driveService = null;

        TaskGetPermissions(GetPermissionsListener listener) {
            this.listener = listener;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            driveService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<Permission> doInBackground(Void... params) {
            try {
                return getPermissions();
            } catch (Exception e) {
                lastError = e;
                cancel(true);
                return null;
            }
        }


        private List<Permission> getPermissions() throws IOException {
            //return driveService.permissions().list(getDatabaseId()).execute().getPermissions();
            List<Permission> permissions = driveService.permissions().list(getDatabaseId())
                    .execute().getItems();

            for(int i = 0; i < permissions.size(); i++)
                if(permissions.get(i).getEmailAddress().equals(googleAccountCredential.getSelectedAccountName()))
                    permissions.remove(i);

            return permissions;
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Collecting accounts...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(List<Permission> output) {
            progressDialog.dismiss();
            if(output == null || output.size() == 0){
                listener.whenFinished(false, null);
            }else {
                listener.whenFinished(true, output);
            }
        }

        @Override
        protected void onCancelled() {
            progressDialog.dismiss();
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    lastError.printStackTrace();
                }
            } else {
                System.err.println("Request to get permissions canceled.");
            }
            listener.whenFinished(false, null);
        }
    }


    private class TaskCheckDatabaseIdValidity extends AsyncTask<Void, Void, Boolean> {

        private CheckDatabaseIdValidityListener listener = null;
        private String databaseId;
        private Exception lastError = null;
        com.google.api.services.drive.Drive driveService = null;
        com.google.api.services.sheets.v4.Sheets sheetsService;

        TaskCheckDatabaseIdValidity(String databaseId, CheckDatabaseIdValidityListener listener) {
            this.databaseId = databaseId;
            this.listener = listener;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            driveService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
            sheetsService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                return checkValidity();
            } catch (Exception e) {
                lastError = e;
                cancel(true);
                return false;
            }
        }


        private Boolean checkValidity() throws IOException{

            Spreadsheet spreadsheet = sheetsService.spreadsheets().get(databaseId).execute();
            if(!spreadsheet.getProperties().getTitle().equals(DATABASE_TITLE))
                return false;

            List<Sheet> sheets = spreadsheet.getSheets();
            if(sheets.size() != 5)
                return false;

            for(Sheet sheet : sheets) {
                String sheetTitle = sheet.getProperties().getTitle();
                if(!(sheetTitle.equals(WALL_REBUILDING_SHEET_NAME)
                        || sheetTitle.equals(CLEANING_SEALING_SHEET_NAME)
                        || sheetTitle.equals(STEP_REBUILDING_SHEET_NAME)
                        || sheetTitle.equals(JOINT_FILL_SHEET_NAME)
                        || sheetTitle.equals(INTERLOCK_RELAYING_SHEET_NAME)))
                    return false;
            }
            try {
                String role = driveService.permissions().get(databaseId, driveService.about().get().execute().getPermissionId()).execute().getRole();
                switch (role) {
                    case "owner":
                        setUserType(USER_TYPE_OWNER);
                        break;
                    default:
                        setUserType(USER_TYPE_EMPLOYEE);
                        break;
                }
            } catch (IOException e){
                setUserType(USER_TYPE_EMPLOYEE);
            }
            return true;
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Checking database ID...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Boolean output) {
            progressDialog.dismiss();
            listener.whenFinished(output, NO_ERROR);
        }

        @Override
        protected void onCancelled() {
            progressDialog.dismiss();
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                    listener.whenFinished(false, NO_GOOGLE_PLAY_SERVICES_ERROR);
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION); // wait until re-called
                } else {
                    lastError.printStackTrace();
                    listener.whenFinished(false, UNKNOWN_ERROR);
                }
            } else {
                System.err.println("Request to get permissions canceled.");
                listener.whenFinished(false, UNKNOWN_ERROR);
            }
        }
    }


    private class TaskGetEstimation extends AsyncTask<Void, Void, Double> {

        private Exception lastError = null;
        private EstimationListener estimationListener = null;
        private boolean accurate = true;
        private List<List<Object>> dataFromSheets;
        private List<Object> newData;

        TaskGetEstimation(List<Object> newData, List<List<Object>> dataFromSheets, EstimationListener estimationListener) {
            this.estimationListener = estimationListener;
            this.dataFromSheets = dataFromSheets;
            this.newData = newData;
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected Double doInBackground(Void... params) {
            try {
                // create new column names since columns are removed (date and estimation ID)

                List<List<Object>> dataSets = new ArrayList<>();
                for(List<Object> dataSet : dataFromSheets){

                    // only get the data sets with a time entered
                    if(dataSet.get(COLUMN_ACTUAL_TIME).equals(""))
                        continue;

                    // Change each value to an int if it is a number or a boolean if it is that
                    dataSet.set(COLUMN_ACTUAL_TIME, Double.parseDouble((String) dataSet.get(COLUMN_ACTUAL_TIME)));
                    dataSet.set(COLUMN_ARIA1, Double.parseDouble((String) dataSet.get(COLUMN_ARIA1)));
                    dataSet.set(COLUMN_ARIA2, Double.parseDouble((String) dataSet.get(COLUMN_ARIA2)));
                    for(int i = COLUMN_ARIA2 + 1; i < dataSet.size(); i++){
                        try {
                            boolean value = toBoolean((String) dataSet.get(i));
                            dataSet.set(i, value);
                        } catch (Exception e) {
                            try {
                                int value = Integer.parseInt((String)dataSet.get(i));
                                dataSet.set(i, value);
                            } catch (Exception ignored){}
                        }
                    }

                    dataSets.add(dataSet);
                }

                // Return if there is not enough data
                if(dataSets.size() < 2) {
                    cancel(true);
                    return null;
                }

                // get the past estimations that can actually be used
                List<List<Object>> usableDataSets = getUsableDataSets(newData, dataSets);

                if(usableDataSets.get(0).size() < 5)
                    accurate = false;

                // Use data to make estimation
                // Take two sets of data at a time to find the two variables; x (which stands for
                // height for instance), and y (length). Then take the averages for each of those
                // variables
                double XTotal = 0;
                int XNum = 0;
                double YTotal = 0;
                int YNum = 0;
                for(int location1 = 0; location1 <= usableDataSets.size() - 2; location1++)
                    for(int location2 = location1 + 1; location2 <= usableDataSets.size() - 1; location2++){
                        List<Object> dataSet1 = usableDataSets.get(location1);
                        List<Object> dataSet2 = usableDataSets.get(location2);
                        // "Eq" stands for equation which in this case is the same as each set of
                        // data or each past estimation.
                        // each x or y here (like in "xEq1") stands for the coefficient. Do not get
                        // this confused with the variables x and y which are being found.
                        double actualTimeEq1 = (double) dataSet1.get(COLUMN_ACTUAL_TIME);
                        double actualTimeEq2 = (double) dataSet2.get(COLUMN_ACTUAL_TIME);
                        double xEq1 = (double) dataSet1.get(COLUMN_ARIA1);
                        double yEq1 = (double) dataSet1.get(COLUMN_ARIA2);
                        double xEq2 = (double) dataSet2.get(COLUMN_ARIA1);
                        double yEq2 = (double) dataSet2.get(COLUMN_ARIA2);
                        // Here's the fun part; finding X and Y
                        // If you where to have two equations
                        // t1 = x1*X + y1*Y
                        // t2 = x2*X + y2*Y
                        // where t1, x1, y1 etc. are all variables; the actual time, the length
                        // dimension for instance that was entered, and the height dimension
                        // respectively. X and Y are then the variables that are being found
                        // When you boil down the equation, you get this:
                        double Y = (actualTimeEq2 - xEq2 * actualTimeEq1 / xEq1)/
                                (-1*yEq1 * xEq2 / xEq1 + yEq2);
                        double X = (actualTimeEq1 - yEq1 * Y)/xEq1;

                        // make sure that the number is real and usable
                        if(Y != Double.NEGATIVE_INFINITY && X != Double.NEGATIVE_INFINITY
                                && Y != Double.POSITIVE_INFINITY && X != Double.POSITIVE_INFINITY
                                && !Double.isNaN(Y) && !Double.isNaN(X)) {
                            XTotal += X;
                            XNum++;
                            YTotal += Y;
                            YNum++;
                        }
                    }
                // Get the averages for both variables
                double XAverage = XTotal/XNum;
                double YAverage = YTotal/YNum;
                // multiply the variables by their coefficients and add the products of that
                return ((Double) newData.get(0)) * XAverage + ((Double) newData.get(1)) * YAverage;
            } catch (Exception e) {
                lastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Collect the data sets which can be used for the estimation recursively
         * @param newDataSet the data which was just collected from the user
         * @param oldDataSets Data from sheets. The minimum number of rows (dataSets) possible is 2
         * @return The usable data sets
         */

        private List<List<Object>> getUsableDataSets(List<Object> newDataSet, List<List<Object>> oldDataSets){

            List<List<Object>> usableDataSets = new ArrayList<>();

            for (int row = 0; row < oldDataSets.size(); row++) {
                List<Object> dataSet = oldDataSets.get(row);
                boolean dataSetValid = true;  // Flag

                for (int itemNum = dataSet.size() - 1; itemNum > 6; itemNum--) {  // start at the end
                    // if all of the items in this row (data set) are not all equal to the items in the
                    // newDataSet (excluding the two aria variables and actual time), then...
                    if (!dataSet.get(itemNum).equals(newDataSet.get(itemNum - 4))) { // subtract 4 for id, date, estimated time, and actual time
                        // This row of data (data set) cannot be used for the estimation while using
                        // this many variables
                        dataSetValid = false;
                        break;
                    }
                }
                // if the row (data set) is invalid then it should not be added to the usableDataSets
                if(dataSetValid)
                    usableDataSets.add(dataSet);
                //Remove last item from olDataSets row for recursive call
                oldDataSets.get(row).remove(dataSet.size()-1);
            }
            // since there are two variables that are being used to find the estimation; length/height
            // and width, the smallest size that the usableDataSets can be is 2
            // if this is not met, re-run the method with the last column gone
            if (usableDataSets.size() >= 2)
                return usableDataSets;
            else {
                return getUsableDataSets(newDataSet, oldDataSets);
            }
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Making Estimation...");
            // progress dialog is still being shown from TaskGetData
            //progressDialog.show();
        }

        @Override
        protected void onPostExecute(Double output) {

            //progressDialog.dismiss();
            estimationListener.whenFinished(true, accurate, output);
        }

        @Override
        protected void onCancelled() {
            //progressDialog.dismiss();
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    lastError.printStackTrace();
                }
            } else {
                final AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
                alertDialog.setTitle("Not enough data");
                alertDialog.setMessage("There is not enough past estimation data that has had the actual time that the job took entered for an estimation to be made.");
                alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        alertDialog.dismiss();
                    }
                });
                alertDialog.show();
            }
            estimationListener.whenFinished(false, false, null);
        }
    }


    private class TaskGetData extends AsyncTask<Void, Void, List<List<Object>>> {

        private com.google.api.services.sheets.v4.Sheets service = null;
        private Exception lastError = null;
        private GetDataListener getDataListener = null;
        private boolean showNoDataDialog, showProgressDialog;

        TaskGetData(boolean showNoDataDialog, boolean showProgressDialog, GetDataListener getDataListener) {
            this.getDataListener = getDataListener;
            this.showNoDataDialog = showNoDataDialog;
            this.showProgressDialog = showProgressDialog;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            service = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<List<Object>> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                lastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         *
         * @return All needed data from given sheet
         */
        private List<List<Object>> getDataFromApi() throws IOException {
            String spreadsheetId = getDatabaseId();
            String range = sheetName+"!A1:Z";
            ValueRange response = this.service.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            return response.getValues();
        }



        @Override
        protected void onPreExecute() {
            if(showProgressDialog) {
                progressDialog.setMessage("Collecting data...");
                progressDialog.show();
            }
        }

        @Override
        protected void onPostExecute(List<List<Object>> output) {
            if (output == null || output.size() < 1) {
                if(showNoDataDialog) {
                    if (currentProcess == GET_DATA_PROCESS && currentHeldProcess != GET_ESTIMATED_TIME_PROCESS)
                        showDialog("There is no data from this job yet.", "No data", OK_ACTION);
                    else if (currentHeldProcess == GET_ESTIMATED_TIME_PROCESS)
                        showDialog("There is no data from this job yet. The estimation cannot be made.", "No data", OK_ACTION);
                }
                getDataListener.whenFinished(false, null);
            } else if (output.size() < 2 && currentHeldProcess == GET_ESTIMATED_TIME_PROCESS && showNoDataDialog) {
                showDialog("There is not enough data from this job yet to get an estimation.", "Not enough data", OK_ACTION);
                getDataListener.whenFinished(false, output);
            } else
                getDataListener.whenFinished(true, output);
        }

        @Override
        protected void onCancelled() {
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    lastError.printStackTrace();
                }
            } else {
                System.err.println("Request to get data was canceled.");
            }
            getDataListener.whenFinished(false, null);
        }
    }


    private class TaskAddEstimation extends AsyncTask<Void, Void, Void> {

        private Sheets service = null;
        private Exception lastError = null;
        private AddEstimationListener addEstimationListener = null;
        private List<List<Object>> estimationData;

        TaskAddEstimation(List<List<Object>> estimationData, AddEstimationListener addEstimationListener) {
            this.estimationData = estimationData;
            this.addEstimationListener = addEstimationListener;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            service = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            try {
                setDataWithApi();
            } catch (Exception e) {
                lastError = e;
                cancel(true);
            }
            return null;
        }

        /**
         * Append estimation data to the end of the Sheet
         */
        private void setDataWithApi() throws IOException {
            String spreadsheetId = getDatabaseId();
            String range = sheetName+"!A1";
            ValueRange requestBody = new ValueRange();
            requestBody.setValues(estimationData);
            requestBody.setMajorDimension("ROWS");
            // Append values to Sheet
            this.service.spreadsheets().values().append(spreadsheetId, range, requestBody)
                    .setValueInputOption("RAW").setInsertDataOption("INSERT_ROWS")
                    .execute();
        }



        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Saving data...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void param) {
            progressDialog.dismiss();
            addEstimationListener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            progressDialog.dismiss();
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    lastError.printStackTrace();
                }
            } else {
                System.err.println("Request to add estimation was canceled.");
            }
            addEstimationListener.whenFinished(false);
        }
    }


    private class TaskRemoveEstimation extends AsyncTask<Void, Void, Void> {

        private Sheets service = null;
        private Exception lastError = null;
        private RemoveEstimationListener listener = null;
        private int estimationId;
        private List<List<Object>> data;

        TaskRemoveEstimation(int estimationId, List<List<Object>> data, RemoveEstimationListener removeEstimationListener) {
            this.estimationId = estimationId;
            this.listener = removeEstimationListener;
            this.data = data;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            service = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            try {
                removeEstimation();
            } catch (Exception e) {
                lastError = e;
                cancel(true);
            }
            return null;
        }

        /**
         * Remove estimation data
         */
        private void removeEstimation() throws IOException {
            //get spreadsheet
            System.out.println("deleting...");
            String spreadsheetId = getDatabaseId();
            Spreadsheet spreadsheet = this.service.spreadsheets().get(spreadsheetId).execute();
            int rowNum = getEstimationRowNum(estimationId, data);
            System.out.println("rowNum = "+rowNum);
            DeleteDimensionRequest ddr = new DeleteDimensionRequest();
            DimensionRange dr = new DimensionRange();
            BatchUpdateSpreadsheetRequest content = new BatchUpdateSpreadsheetRequest();
            dr.setDimension("ROWS")
                    .setStartIndex(rowNum-1)
                    .setEndIndex(rowNum)
                    .setSheetId(spreadsheet.getSheets().get(sheetId).getProperties().getSheetId());
            ddr.setRange(dr);
            Request request = new Request();
            request.setDeleteDimension(ddr);
            List<Request> requests = new ArrayList<>();
            requests.add(request);
            content.setRequests(requests);
            // Remove data from Sheet
            service.spreadsheets().batchUpdate(spreadsheetId, content).execute();
        }



        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Removing data...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void param) {
            progressDialog.dismiss();
            System.out.println("success");
            listener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            progressDialog.dismiss();
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    lastError.printStackTrace();
                }
            } else {
                System.err.println("Request to add estimation was canceled.");
            }
            listener.whenFinished(false);
        }
    }


    private class TaskSetActualTime extends AsyncTask<Void, Void, Void> {

        private Sheets service = null;
        private Exception lastError = null;
        private SetActualTimeListener setActualTimeListener = null;
        private List<List<Object>> data;
        private int estimationId;

        TaskSetActualTime(double totalHours, int estimationId, List<List<Object>> data, SetActualTimeListener setActualTimeListener) {
            //put totalHours into a List
            List<List<Object>> lists = new ArrayList<>();
            List<Object> list = new ArrayList<>();
            list.add(totalHours);
            lists.add(list);
            this.data = data;

            this.estimationId = estimationId;
            this.data = lists;
            this.setActualTimeListener = setActualTimeListener;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            service = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, googleAccountCredential)
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            try {
                setDataWithApi();
            } catch (Exception e) {
                lastError = e;
                cancel(true);
            }
            return null;
        }

        /**
         * Put time on Sheet
         */
        private void setDataWithApi() throws IOException {
            String spreadsheetId = getDatabaseId();
            String range = sheetName+"!D"+getEstimationRowNum(estimationId, pastEstimationData)+":D";
            ValueRange requestBody = new ValueRange();
            requestBody.setValues(data);
            requestBody.setMajorDimension("ROWS");
            // Append values to Sheet
            this.service.spreadsheets().values().update(spreadsheetId, range, requestBody)
                    .setValueInputOption("RAW").execute();
        }



        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Saving data...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void param) {
            progressDialog.dismiss();
            setActualTimeListener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            progressDialog.dismiss();
            if (lastError != null) {
                if (lastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) lastError)
                                    .getConnectionStatusCode());
                } else if (lastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) lastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    lastError.printStackTrace();
                }
            } else {
                System.err.println("Request to add estimation was canceled.");
            }
            setActualTimeListener.whenFinished(false);
        }
    }
}


/*
Interfaces; used as listeners
 */

/**
 * base listener. not used for anything exept so that the other listeners can extend this one
 * so that there can be one currentListener; not one for each type
 */

interface Listener {

}

interface CreateDatabaseListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success, int errorId);
}

interface AddPermissionsListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success);
}

interface RemovePermissionListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success);
}

interface GetPermissionsListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success, List<Permission> permissions);
}

interface CheckDatabaseIdValidityListener extends Listener {
    /**
     * Called when the currently running process if finished.
     */
    void whenFinished(boolean validId, int errorId);
}

interface GetDataListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     * @param output the results of the process.
     */
    void whenFinished(boolean success, List<List<Object>> output);
}

interface EstimationListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     * @param accurate whether the output is very accurate (used for startEstimation).
     * @param estimatedHours the results of the process.
     */
    void whenFinished(boolean success, boolean accurate, Double estimatedHours);
}

interface AddEstimationListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success);
}

interface RemoveEstimationListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success);
}

interface SetActualTimeListener extends Listener {/**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success);
}