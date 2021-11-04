# DeDupeUtility
code challenge from Marketo/Adobe interview.

To run the code use these 2 lines (you will need maven installed):
> mvn install
> mvn compile exec:java -Dexec.mainClass="com.adobe.DeDupeUtility" -Dexec.args="code_challenge_leads.json output.json change.log"

if you want to use a different input file, change 1st parameter in  `-Dexec.args`

the 2nd and 3rd parameters are the name of the de-duped output json file, and the output of the changelog from execution.
They are optional.

Example:
> input:     `-Dexec.args="your_filename.json"` 
> 
> output:    `your_filenameOutput.json`
> 
> changelog: `your_filenameChange.log`

I have comments in the code,
And was able to get the execution down to O(2n) by using HashMap to track id & email lists for detecting duplicates.
I tried to figure out if it was possible to get it to O(n) but couldn't think of a way, that would still maintain the order of the input list.

Assumptions:
- All leads will have "_id" and "email" values. (other values are maintained from winning lead, but not edited)
- Json will be same format as the given json file i.e. the values we care about are always in "leads" area of json.
- Date format (in "entryDate") is always in the format in example file.
 - "yyyy-MM-dd'T'kk:mm:ss+SS:00" in SimpleDateFormat pattern
 - If DateFormat was changed, `isFirstWinner(..)` method would need to be edited
