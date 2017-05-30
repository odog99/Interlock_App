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
import android.content.SharedPreferences;
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
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
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

    private static final int COLUMN_ESTIMATION_ID = 0;
    private static final int COLUMN_DATE = 1;
    private static final int COLUMN_ACTUAL_TIME = 2;
    private static final int COLUMN_ARIA = 3;

    private int currentProcess = NO_PROCESS;
    private int currentHeldProcess = NO_PROCESS;

    static final String DATABASE_ID_FILE_NAME = "database_id";
    static final String USER_TYPE_FILE_NAME = "user_type";

    static final String USER_TYPE_OWNER = "owner";
    static final String USER_TYPE_EMPLOYEE = "employee";

    private int sheetId;
    private String sheetName;
    private String properSheetName;
    private Activity activity;
    private Listener currentListener;
    private List<Object> currentEstimationData;
    private int currentEstimationId;
    private int currentActualTime;
    private List<String> currentEmails;
    private List<Permission> currentPermissions;
    private String currentDatabaseId;
    private int variableNum;
    private int nextEstimationId = -1;
    private List<List<Object>> pastEstimationData;

    private static final int OK_ACTION = 0;
    private static final int RETRY_ACTION = 1;

    static final int ID_NOT_APPLICABLE = -1;
    static final int WALL_REBUILDING_ID = 0;
    static final int CLEANING_SEALING_ID = 1;
    static final int STEP_REBUILDING_ID = 2;
    static final int JOINT_FILL_ID = 3;
    static final int INTERLOCK_RELAYING_ID = 4;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE_FILE};

    /**
     *
     * @param sheetId The ID of the Google Sheet. Use the ID's in this file (<SHEET_NAME>_ID)
     * @param activity The Activity that uses the sheet. Used for dialogs etc. You can usually use 'this'
     */

    EstimationSheet(int sheetId, Activity activity){
        this.sheetId = sheetId;
        this.activity = activity;

        switch (sheetId){
            case ID_NOT_APPLICABLE:
                sheetName = "";
                properSheetName = "";
                variableNum = -1;
                break;
            case WALL_REBUILDING_ID:
                sheetName = "wall_rebuilding";
                properSheetName = "Wall Rebuilding";
                variableNum = 11;
                break;
            case CLEANING_SEALING_ID:
                sheetName = "cleaning_sealing";
                properSheetName = "Cleaning and Sealing";
                variableNum = 7;
                break;
            case STEP_REBUILDING_ID:
                sheetName = "step_rebuilding";
                properSheetName = "Step Rebuilding";
                //TODO change this to be the actual number of variables
                variableNum = -1;
                break;
            case JOINT_FILL_ID:
                sheetName = "joint_fill";
                properSheetName = "Joint Fill";
                //TODO change this to be the actual number of variables
                variableNum = -1;
                break;
            case INTERLOCK_RELAYING_ID:
                sheetName = "interlock_relaying";
                properSheetName = "Interlock Relaying";
                //TODO change this to be the actual number of variables
                variableNum = -1;
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
     * This should only be called when there is no database made yet
     * @param listener the CreateDatabaseListener that is notified when task is finished.
     */

    void startCreatingDatabase(final CreateDatabaseListener listener){
        if(currentProcess != NO_PROCESS && currentProcess != CREATE_DATABASE_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = CREATE_DATABASE_PROCESS;
        currentListener = listener;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            listener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to store data.", RETRY_ACTION);
        }
        else{
            TaskCreateDatabase taskCreateDatabase = new TaskCreateDatabase(new CreateDatabaseListener() {
                @Override
                public void whenFinished(boolean success) {
                    currentProcess = NO_PROCESS;

                    listener.whenFinished(success);
                }
            });
            taskCreateDatabase.execute();
        }
    }


    void startAddingPermissions(final List<String> emails, final AddPermissionsListener listener){
        if(currentProcess != NO_PROCESS && currentProcess != ADD_PERMISSIONS_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = ADD_PERMISSIONS_PROCESS;
        currentEmails = emails;
        currentListener = listener;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            listener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to store data.", RETRY_ACTION);
        }
        else{
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


    void startRemovingPermissions(final List<Permission> permissions, final RemovePermissionsListener listener){
        if(currentProcess != NO_PROCESS && currentProcess != REMOVE_PERMISSIONS_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = REMOVE_PERMISSIONS_PROCESS;
        currentPermissions = permissions;
        currentListener = listener;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            listener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to store data.", RETRY_ACTION);
        }
        else{
            TaskRemovePermissions taskRemovePermissions = new TaskRemovePermissions(permissions, new RemovePermissionsListener() {
                @Override
                public void whenFinished(boolean success) {
                    currentProcess = NO_PROCESS;
                    listener.whenFinished(success);
                }
            });
            taskRemovePermissions.execute();
        }
    }


    void startGettingPermissions(final GetPermissionsListener listener){
        if(currentProcess != NO_PROCESS && currentProcess != GET_PERMISSIONS_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = GET_PERMISSIONS_PROCESS;
        currentListener = listener;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            listener.whenFinished(false, null);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to store data.", RETRY_ACTION);
        }
        else{
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
        if(currentProcess != NO_PROCESS && currentProcess != CHECK_DATABASE_ID_VALIDITY_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = CHECK_DATABASE_ID_VALIDITY_PROCESS;
        currentListener = listener;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            listener.whenFinished(false, null);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to store data.", RETRY_ACTION);
        }
        else{
            TaskCheckDatabaseIdValidity taskCheckDatabaseIdValidity = new TaskCheckDatabaseIdValidity(databaseId, new CheckDatabaseIdValidityListener() {
                @Override
                public void whenFinished(boolean success, Boolean validId) {
                    currentProcess = NO_PROCESS;
                    listener.whenFinished(success, validId);
                }
            });
            taskCheckDatabaseIdValidity.execute();
        }
    }

    /**
     *
     * @param data the data to make estimation with
     * @param estimationListener GetDataListener for what to do when finished getting estimation
     */
    void startEstimation(final List<Object> data, final EstimateListener estimationListener){
        if(currentProcess != NO_PROCESS && currentProcess != GET_ESTIMATED_TIME_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        try {
            currentHeldProcess = GET_ESTIMATED_TIME_PROCESS;
            GetDataListener getDataGetDataListener = new GetDataListener() {
                @Override
                public void whenFinished(boolean success, List<List<Object>> output) {
                    //finished getting data from Sheets
                    currentProcess = GET_ESTIMATED_TIME_PROCESS;
                    currentHeldProcess = NO_PROCESS;
                    //update progressDialog message
                    progressDialog.setMessage("Making estimation...");
                    boolean accurate;
                    double estimatedTime;
                    if (success) {
                        List<List<Object>> pastEstimations = (ArrayList) output;
                        List<List<Object>> dataSets = new ArrayList<>();
                        for(List<Object> estimation : pastEstimations){
                            if(!estimation.get(COLUMN_ACTUAL_TIME).equals(""))//a time has been entered
                                dataSets.add(estimation);
                        }
                        if(dataSets.size() < variableNum){
                            //use only the aria to find estimated time
                            accurate = false;
                            double sumAlphas = 0;
                            int numAlphas = 0;
                            for(List<Object> dataSet : dataSets){
                                double actualTime = Double.parseDouble((String) dataSet.get(COLUMN_ACTUAL_TIME));
                                double aria = Double.parseDouble((String) dataSet.get(COLUMN_ARIA));
                                sumAlphas += actualTime/aria;
                                numAlphas ++;
                            }
                            double alpha = sumAlphas/numAlphas;//average of all alphas
                            estimatedTime = alpha*(double)data.get(0);
                        }else{
                            //TODO fix startEstimation() for when estimation should be accurate
                            //use all data
                            accurate = true;
                            estimatedTime = -1;
                        }
                        estimationListener.whenFinished(true, accurate, estimatedTime);
                    } else {
                        //abort startEstimation; an error has occurred
                        estimationListener.whenFinished(false, false, null);
                    }
                    currentProcess = NO_PROCESS;
                    currentHeldProcess = NO_PROCESS;
                    progressDialog.hide();
                }
            };
            currentListener = getDataGetDataListener;
            try{
                getData(getDataGetDataListener);
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
        if(currentProcess != NO_PROCESS && currentProcess != ADD_ESTIMATION_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = ADD_ESTIMATION_PROCESS;

        // Add estimation id, date, and empty space (for where the actual time will be) to data
        data.add(0, nextEstimationId);
        data.add(1,new SimpleDateFormat("dd/MM/yyyy").format(Calendar.getInstance(
                TimeZone.getTimeZone("America/Montreal")).getTime()));
        data.add(2, "");

        currentEstimationData = data;
        currentListener = addEstimationListener;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            addEstimationListener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to store data.", RETRY_ACTION);
        }
        else{
            progressDialog.setMessage("Storing data...");
            List<List<Object>> preparedData = new ArrayList<>();
            preparedData.add(data);
            TaskAddEstimation taskAddEstimation = new TaskAddEstimation(preparedData, new AddEstimationListener() {
                @Override
                public void whenFinished(boolean success) {
                    currentProcess = NO_PROCESS;
                    progressDialog.hide();
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
    void startRemovingEstimation(int estimationId, final RemoveEstimationListener removeEstimationListener){
        if(currentProcess != NO_PROCESS && currentProcess != REMOVE_ESTIMATION_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = REMOVE_ESTIMATION_PROCESS;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            removeEstimationListener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to store data.", RETRY_ACTION);
        }
        else{
            TaskRemoveEstimation taskRemoveEstimation = new TaskRemoveEstimation(estimationId, new RemoveEstimationListener() {
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
    void startSettingActualTime(int estimationId, double totalHours, final SetActualTimeListener setActualTimeListener){
        if(currentProcess != NO_PROCESS && currentProcess != REMOVE_ESTIMATION_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = REMOVE_ESTIMATION_PROCESS;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            setActualTimeListener.whenFinished(false);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to store data.", RETRY_ACTION);
        }
        else{
            TaskSetActualTime taskSetActualTime = new TaskSetActualTime(totalHours, estimationId, new SetActualTimeListener() {
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
    void startGettingData(final GetDataListener getDataListener){
        getData(new GetDataListener() {
            @Override
            public void whenFinished(boolean success, List<List<Object>> output) {
                progressDialog.hide();
                getDataListener.whenFinished(success, output);
            }
        });
    }

    boolean doesUserHaveRole(){
        return getUserType() != null;
    }

    boolean isDatabaseIdSaved(){
        return getDatabaseId() != null;
    }

    boolean isUserOwner(){
        String userType = getUserType();
        if(userType.equals(USER_TYPE_OWNER))
            return true;
        else if (userType.equals(USER_TYPE_EMPLOYEE))
            return false;
        else if (userType == null || userType.equals(""))
            throw new IllegalStateException("There is either no user type file, or there is no data in it.");
        else
            throw new IllegalStateException("The data '"+userType+"'  which is in the user type file is not valid.");
    }

    void saveUserType(String userType){
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
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle("Error");
        alertDialog.setMessage(error);
        if(action == OK_ACTION)
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
        else if(action == RETRY_ACTION) {
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "RETRY",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            resumeProcess();
                        }
                    });
        }
        else
            throw new IllegalArgumentException("'"+action+"' is an invalid action. Please use one of the given actions which are in the format <ACTION>_ACTION.");

        alertDialog.show();
    }

    private void resumeProcess(){
        switch (currentProcess) {
            case GET_DATA_PROCESS:
                getData((GetDataListener) currentListener);
                break;
            case GET_ESTIMATED_TIME_PROCESS:
                getData((GetDataListener) currentListener);
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
                startRemovingPermissions(currentPermissions, (RemovePermissionsListener) currentListener);
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
    private void getData(final GetDataListener getDataListener) {
        if(currentProcess != NO_PROCESS && currentProcess != GET_DATA_PROCESS)
            throw new IllegalStateException("Cannot be running multiple processes at once. Please do not start this processes until the previous one is done. Was running processes number '"+currentProcess+"'");
        currentProcess = GET_DATA_PROCESS;
        if (! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
            getDataListener.whenFinished(false, null);
        }
        else if (googleAccountCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (! isDeviceOnline()) {
            showErrorDialog("No network connection available. Interlock App requires there to be an internet connection to get an estimation.", RETRY_ACTION);
        }
        else{
            progressDialog.setMessage("Collecting data for estimation...");
            TaskGetData taskGetData = new TaskGetData(new GetDataListener() {
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
        int maxId = -2;// will return -1 if not found
        try {
            maxId = Integer.parseInt((String) data.get(data.size() - 1).get(COLUMN_ESTIMATION_ID));
        } catch (Exception e){
            e.printStackTrace();
        }
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
            String accountName = activity.getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
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
                        SharedPreferences settings =
                                activity.getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
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
    private boolean isGooglePlayServicesAvailable() {
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
    private void acquireGooglePlayServices() {
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
            try {
                String databaseId = createDatabase();
                saveDatabaseId(databaseId);
            } catch (Exception e) {
                lastError = e;
                cancel(true);
            }
            return null;
        }


        private String createDatabase() throws IOException {
            // Create Sheets
            List<Sheet> sheets = new ArrayList<>();
            // Wall rebuilding
            SheetProperties wallRebuildingProperties = new SheetProperties();
            wallRebuildingProperties.setTitle("wall_rebuilding");
            Sheet wallRebuildingSheet = new Sheet();
            wallRebuildingSheet.setProperties(wallRebuildingProperties);
            sheets.add(wallRebuildingSheet);
            // Cleaning sealing
            SheetProperties cleaningSealingProperties = new SheetProperties();
            cleaningSealingProperties.setTitle("cleaning_sealing");
            Sheet cleaningSealingSheet = new Sheet();
            cleaningSealingSheet.setProperties(cleaningSealingProperties);
            sheets.add(cleaningSealingSheet);
            // Step rebuilding
            SheetProperties stepRebuildingProperties = new SheetProperties();
            stepRebuildingProperties.setTitle("step_rebuilding");
            Sheet stepRuibuildingSheet = new Sheet();
            stepRuibuildingSheet.setProperties(stepRebuildingProperties);
            sheets.add(stepRuibuildingSheet);
            // Joint fill
            SheetProperties jointFillProperties = new SheetProperties();
            jointFillProperties.setTitle("joint_fill");
            Sheet jointFillSheet = new Sheet();
            jointFillSheet.setProperties(jointFillProperties);
            sheets.add(jointFillSheet);
            // Interlock relaying
            SheetProperties interlockRelayingProperties = new SheetProperties();
            interlockRelayingProperties.setTitle("interlock_relaying");
            Sheet interlockRelayingSheet = new Sheet();
            interlockRelayingSheet.setProperties(interlockRelayingProperties);
            sheets.add(interlockRelayingSheet);

            // Setup properties
            SpreadsheetProperties spreadsheetProperties = new SpreadsheetProperties();
            spreadsheetProperties.setTitle("Interlock App Database");

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
            progressDialog.hide();
            createDatabaseListener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            progressDialog.hide();
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
                System.err.println("Request to create Spreadsheet canceled.");
            }
            createDatabaseListener.whenFinished(false);
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
            try {
                setPermissions();
            } catch (Exception e) {
                lastError = e;
                cancel(true);
            }
            return null;
        }


        private void setPermissions() throws IOException {
            String fileId = getDatabaseId();
            for(String email : emails) {
                Permission permission = new Permission();
                permission.setType("user");
                permission.setRole("writer");
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
                driveService.permissions().create(fileId, permission).setSendNotificationEmail(true)
                        .setEmailMessage(emailMessage).execute();
            }
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Giving accounts access to database...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void output) {
            progressDialog.hide();
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
            progressDialog.hide();
            listener.whenFinished(false);
        }
    }


    private class TaskRemovePermissions extends AsyncTask<Void, Void, Void> {

        private RemovePermissionsListener listener = null;
        private Exception lastError = null;
        com.google.api.services.drive.Drive driveService = null;
        private List<Permission> permissions = null;

        TaskRemovePermissions(List<Permission> permissions, RemovePermissionsListener listener) {
            this.listener = listener;
            this.permissions = permissions;
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
            String fileId = getDatabaseId();
            for(Permission permission : permissions) {
                driveService.permissions().delete(fileId, permission.getId()).execute();
            }
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Removing database access for accounts...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void output) {
            progressDialog.hide();
            listener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            progressDialog.hide();
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
            return driveService.permissions().list(getDatabaseId()).execute().getPermissions();
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Collecting accounts...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(List<Permission> output) {
            progressDialog.hide();
            if(output == null || output.size() == 0){
                listener.whenFinished(false, null);
            }else {
                listener.whenFinished(true, output);
            }
        }

        @Override
        protected void onCancelled() {
            progressDialog.hide();
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

        TaskCheckDatabaseIdValidity(String databaseId, CheckDatabaseIdValidityListener listener) {
            this.databaseId = databaseId;
            this.listener = listener;
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            driveService = new com.google.api.services.drive.Drive.Builder(
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
                return null;
            }
        }


        private Boolean checkValidity() throws IOException{
            File file = driveService.files().get(databaseId).execute();
            return file.getCapabilities().getCanEdit();
        }


        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Checking database ID...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Boolean output) {
            progressDialog.hide();
            listener.whenFinished(true, output);
        }

        @Override
        protected void onCancelled() {
            progressDialog.hide();
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


    private class TaskGetData extends AsyncTask<Void, Void, List<List<Object>>> {

        private com.google.api.services.sheets.v4.Sheets service = null;
        private Exception lastError = null;
        private GetDataListener getDataListener = null;

        TaskGetData(GetDataListener getDataListener) {
            this.getDataListener = getDataListener;
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
            String range = sheetName+"!A2:N";
            ValueRange response = this.service.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            return response.getValues();
        }



        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Collecting data...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(List<List<Object>> output) {
            if (output == null || output.size() < 1) {
                if(currentProcess == GET_DATA_PROCESS)
                    showErrorDialog("There is no data from this job yet.", OK_ACTION);
                else if(currentHeldProcess == GET_ESTIMATED_TIME_PROCESS)
                    showErrorDialog("There is no data from this job yet. The estimation cannot be made.", OK_ACTION);
                getDataListener.whenFinished(false, null);
            } else if (output.size() < 2 && currentHeldProcess == GET_ESTIMATED_TIME_PROCESS){
                showErrorDialog("There is not enough data from this job yet to get an estimation.", OK_ACTION);
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
            String range = sheetName+"!A:N";
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
            progressDialog.hide();
            addEstimationListener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            progressDialog.hide();
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

        TaskRemoveEstimation(int estimationId, RemoveEstimationListener removeEstimationListener) {
            this.estimationId = estimationId;
            this.listener = removeEstimationListener;
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
         * Append estimation data to the end of the Sheet
         */
        private void removeEstimation() throws IOException {
            int rowNum = getEstimationRowNum(estimationId, pastEstimationData);
            String spreadsheetId = getDatabaseId();
            String range = sheetName+"!"+rowNum+"A:"+rowNum+"N";
            ClearValuesRequest requestBody = new ClearValuesRequest();
            // Remove data from Sheet
            this.service.spreadsheets().values().clear(spreadsheetId, range, requestBody)
                    .execute();
        }



        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Removing data...");
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(Void param) {
            progressDialog.hide();
            listener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            progressDialog.hide();
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

        TaskSetActualTime(double totalHours, int estimationId, SetActualTimeListener setActualTimeListener) {
            //put totalHours into a List
            List<List<Object>> lists = new ArrayList<>();
            List<Object> list = new ArrayList<>();
            list.add(totalHours);
            lists.add(list);

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
            String range = sheetName+"!C"+getEstimationRowNum( estimationId, pastEstimationData)+":C";
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
            progressDialog.hide();
            setActualTimeListener.whenFinished(true);
        }

        @Override
        protected void onCancelled() {
            progressDialog.hide();
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

interface CreateDatabaseListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success);
}

interface AddPermissionsListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success);
}

interface RemovePermissionsListener extends Listener {
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
     * @param success whether the process was successful or not.
     */
    void whenFinished(boolean success, Boolean validId);
}

interface GetDataListener extends Listener {
    /**
     * Called when the currently running process if finished.
     * @param success whether the process was successful or not.
     * @param output the results of the process.
     */
    void whenFinished(boolean success, List<List<Object>> output);
}

interface EstimateListener extends Listener {
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

interface Listener {

}