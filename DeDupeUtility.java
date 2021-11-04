/* Created by: Nick Patron (patron.nick@gmail.com)
 * Any usage without permission is not cool, -- I would probably give it if asked.
 */
package com.adobe;

import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.String.*;

/**
 * Console line Java Application
 * that will read a json file,
 * and remove duplicates (same "_id" or "email")
 * keeping only the most recent (or later in json if same date/time)
 *
 * Assumptions:
 *  All leads will have a "ChangeDate" entry in the same date format
 *
 * to run this use maven.
 *  from main directory.
 *  run :
 *    mvn clean install
 *    mvn compile exec:java -Dexec.mainClass="com.adobe.DeDupeUtility" -Dexec.args="code_challenge_leads.json output.json change.log"
 *     - if you want to use different command line args, change the values in -Dexec.args="XX.json"
 *
 * @author Nick Patron
 */
public class DeDupeUtility {

    public static void main(String[] args) {
        for(String arg: args){
            System.out.println(arg);
        }

        if (args.length < 1) {
            System.out.println("error: Not Enough arguments.");
            printUsage();
            return;
        }
        if (args[0].compareTo("--help")==0){
            printUsage();
            return;
        }
        if (!args[0].endsWith(".json")){
            System.out.println("error: invalid input filetype. `.json` expected.");
            printUsage();
            return;
        }
        final String inputPath = args[0];
        final String outputPath;
        final String logToPath;

        if (args.length > 2){
            outputPath = args[1];
            logToPath = args[2];
        } else if (args.length == 2) {
            outputPath = args[1];
            logToPath = inputPath.replace(".json", "Change.log");
        } else {
            outputPath = inputPath.replace(".json", "Out.json");
            logToPath = inputPath.replace(".json", "Change.log");
        }

        try {
            JSONObject inputJson = parseJsonFile(inputPath);
            JSONObject outputJson = deDupeJson(inputJson, logToPath);
            exportJsonToFile(outputJson, outputPath);
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public static JSONObject parseJsonFile(String inputPath) throws IOException {
        String jsonString = new String(Files.readAllBytes(Paths.get(inputPath)));
        return new JSONObject(jsonString);
    }

    /**
     * Take JSONObject, and export it into outputPath, with some indentation.
     * @param json - JSONObject to write to output file
     * @param outputPath - fileName to put updated Json into (ex. exampleOut.json)
     * @throws IOException - if an I/O error occurs creating the file at outputPath
     */
    public static void exportJsonToFile(JSONObject json, String outputPath) throws IOException {
        BufferedWriter outputWriter = Files.newBufferedWriter(Paths.get(outputPath));
        json.write(outputWriter, 1, 1);
        outputWriter.close();
    }

    /**
     * Method to remove Duplicate leads from a JSONObject.
     * It will also log to the console & logToPath any edits made to the JSONObject.
     * this will iterate through the JSONObject "Leads": list,
     * then checks the _id & email for each against private idList & emailList
     *      if _id or email match existing email/_id, will add this to list of duplicates `dupeList`
     * once all duplicates have been found for this lead, will determine winner, and update removeLeads list.
     *
     * @param jsonObject - JSONObject containing "leads" list that will have duplicates removed.
     * @param logToPath - path for logging any changes made.
     *                  the changes will also be posted to console via `System.out.print()`
     * @return Updated JsonObject with duplicates removed.
     * @throws IOException - if an I/O error occurs creating the logToPath file.
     */
    public static JSONObject deDupeJson(JSONObject jsonObject, String logToPath) throws IOException {
        BufferedWriter logWriter = new BufferedWriter(new FileWriter(logToPath));
        HashMap<Integer, Pair<Integer, Boolean>> toBeRemovedLeads = new HashMap<>();
        JSONArray leads = jsonObject.getJSONArray("leads");

        // use Map for email & id to make checking for duplicates faster.
        Map<String, Integer> emailMap = new HashMap<>();
        Map<String, Integer> idMap = new HashMap<>();
        for(int currentLeadIndex =0; currentLeadIndex< leads.length(); currentLeadIndex++){
            JSONObject currentLeadJson = leads.getJSONObject(currentLeadIndex);

            // read current id & email from json
            String email = currentLeadJson.getString("email");
            String id = currentLeadJson.getString("_id");

            //// check Maps for exiting duplicates. ////
            // if `email` matches already added `email`, mark the index for de-dupe.
            int emailDupeIndex = -1;
            if (emailMap.containsKey(email)) {
                emailDupeIndex = emailMap.get(email);
            } else {
                emailMap.put(email, currentLeadIndex);
            }
            // if `_id` matches already added `_id`, mark the index for de-dupe.
            int idDupeIndex = -1;
            if (idMap.containsKey(id)) {
                idDupeIndex = idMap.get(id);
            } else {
                idMap.put(id, currentLeadIndex);
            }
            if (emailDupeIndex>-1){
                try {
                    boolean currentWins = isFirstWinner(currentLeadJson, leads.getJSONObject(emailDupeIndex));
                    if (currentWins) {
                        // add found lead `emailDupeIndex` to `toBeRemovedLeads`, with reason -> Pair (`currentLeadJson`, `true` - due to email)
                        toBeRemovedLeads.put(emailDupeIndex, new Pair<>(currentLeadIndex, true));
                        emailMap.put(email, currentLeadIndex);
                        idMap.put(id, currentLeadIndex);
                    } else {
                        // add `currentLeadIndex` to `toBeRemovedLeads`, with reason -> Pair (`dupeLeadIndex`, `true` - due to email)
                        toBeRemovedLeads.put(currentLeadIndex, new Pair<>(emailDupeIndex, true));
                        emailMap.put(email, emailDupeIndex);
                        // make sure removed lead's id isn't added
                        idMap.remove(id);
                    }
                } catch (ParseException e){
                    logWriter.write(format("Unable to determine winner for (index %03dvs%03d) - Exception:%s",
                            currentLeadIndex, emailDupeIndex, e.getMessage()));
                    e.printStackTrace();
                }
            }
            if (idDupeIndex> -1 && emailDupeIndex != idDupeIndex){ // if different index, check that as well.
                try {
                    boolean currentWins = isFirstWinner(currentLeadJson, leads.getJSONObject(idDupeIndex));
                    if (currentWins) {
                        // add `idDupeIndex` to `toBeRemovedLeads`, with reason -> Pair (`currentLeadJson`, `false` - due to id)
                        toBeRemovedLeads.put(idDupeIndex, new Pair<>(currentLeadIndex, false));
                        emailMap.put(email, currentLeadIndex);
                        idMap.put(id, currentLeadIndex);
                    } else {
                        // mark `currentLeadIndex` to `toBeRemovedLeads`, with reason -> Pair (`idDupeIndex`, `false` - due to id)
                        toBeRemovedLeads.put(currentLeadIndex, new Pair<>(idDupeIndex, false));
                        emailMap.remove(id);
                    }
                } catch (ParseException e){
                    logWriter.write(format("Unable to determine winner for (index %03dvs%03d) - Exception:%s",
                            currentLeadIndex, idDupeIndex, e.getMessage()));
                    e.printStackTrace();
                }
            }
        }

        // build new outLeads out of only non-toBeRemoved leads, but keep the order.
        JSONArray outLeads = new JSONArray();
        for(int i =0; i < leads.length(); i ++){
            if (!toBeRemovedLeads.containsKey(i)) {
                outLeads.put(leads.getJSONObject(i));
            } else {
                // don't add this to outLeads, instead add logging.

                JSONObject leadInfo = leads.getJSONObject(i);
                // log removing lead
                String logOut = format("-      Removing Lead at index:%2d > %s\n", i, leadInfo.toString());

                // log reason for removal
                Integer reasonIndex = toBeRemovedLeads.get(i).getKey();
                String logReason = toBeRemovedLeads.get(i).getValue()?"email":"_id ";
                JSONObject reasonLeadInfo = leads.getJSONObject(reasonIndex);
                logOut += format("* Same %5s to Lead at index:%2d > %s\n", logReason, reasonIndex, reasonLeadInfo.toString());

                logWriter.write(logOut);
                System.out.print(logOut);
            }
        }
        logWriter.close();

        // return it in JSONObject format (similar to input)
        return new JSONObject().put("leads", outLeads);
    }

    /**
     * compares date values for 2 JSONObjects.
     * it uses a hardcoded datePattern ("yyyy-MM-dd'T'kk:mm:ss+SS:00"), to compare the "entryDate" value
     *
     * This will return whether the first is newest, or not.
     * for DeDupeUtility, we use this to determine the winner, between 2 "leads"
     * the winner is either the newest, or the 2nd (in case of tie)
     *
     * @param firstJSONObject jsonObject, to read "entryDate" from
     * @param secondJSONObject jsonObject, to read "entryDate" from
     * @return if first JsonObject's "entryDate" is newer than 2nd JsonObject's "entryDate"
     * @throws ParseException - if unable to parse entryDate values.
     */
    public static boolean isFirstWinner(JSONObject firstJSONObject, JSONObject secondJSONObject) throws ParseException {
        String datePattern = "yyyy-MM-dd'T'kk:mm:ss+SS:00";
        SimpleDateFormat dateFormat = new SimpleDateFormat(datePattern);
        Date firstDate = dateFormat.parse(firstJSONObject.getString("entryDate"));
        Date secondDate = dateFormat.parse(secondJSONObject.getString("entryDate"));
        // return true only if firstDate is later than secondDate
        return firstDate.compareTo(secondDate)>= 0;
    }

    /**
     * helper for printing usage message to console.
     */
    static void printUsage(){
        System.out.println("Usage: java DeDupeUtility.java <importFilename.json> <destinationFilename.json> <change.log>");
        System.out.println("Remove duplicate entries from import.json");
        System.out.println("<destinationFileName> and <change.log> are optional. if not supplied, will create based upon `importFilename.json`");
    }

}