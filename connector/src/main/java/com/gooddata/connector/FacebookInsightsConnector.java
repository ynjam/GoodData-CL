/*
 * Copyright (c) 2009, GoodData Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice, this list of conditions and
 *        the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 *        and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *     * Neither the name of the GoodData Corporation nor the names of its contributors may be used to endorse
 *        or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.gooddata.connector;

import com.gooddata.exception.*;
import com.gooddata.modeling.model.SourceColumn;
import com.gooddata.modeling.model.SourceSchema;
import com.gooddata.processor.CliParams;
import com.gooddata.processor.Command;
import com.gooddata.processor.ProcessingContext;
import com.gooddata.util.CSVWriter;
import com.gooddata.util.FileUtil;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * GoodData Facebook Insights connector
 *
 * @author zd <zd@gooddata.com>
 * @version 1.0
 */
public class FacebookInsightsConnector extends AbstractConnector implements Connector {


    private static Logger l = Logger.getLogger(FacebookInsightsConnector.class);

    private String oauthToken;
    private DateTime startDate;
    private DateTime endDate;
    private String baseUrl;
    private HttpClient client;

    private final DateTimeFormatter baseFmt = DateTimeFormat.forPattern(Constants.DEFAULT_DATETIME_FMT_STRING);
    private final DateTime base = baseFmt.parseDateTime("1900-01-01 00:00:00");


    protected FacebookInsightsConnector() {
        client = new HttpClient();

        final String proxyHost = System.getProperty("http.proxyHost");
        final int proxyPort = System.getProperty("http.proxyPort") == null
            ? 8080 : Integer.parseInt(System.getProperty("http.proxyPort"));

        if (proxyHost != null) {
            client.getHostConfiguration().setProxy(proxyHost,proxyPort);
        }

    }

    /**
     * Creates a new Facebook connector
     * @return a new instance of the FacebookConnector
     */
    public static FacebookInsightsConnector createConnector() {
        return new FacebookInsightsConnector();
    }

    /**
     * Saves a template of the config file
     * @param name new schema name
     * @param configFileName config file name
     * @throws java.io.IOException if there is a problem with writing the config file
     * @throws java.sql.SQLException if there is a problem with the db
     */
    public static void saveConfigTemplate(String name, String configFileName, String folder)
            throws IOException {
        l.debug("Saving Facebook config template.");
        final SourceSchema s = SourceSchema.createSchema(name);

        SourceColumn o = new SourceColumn("objectid", SourceColumn.LDM_TYPE_DATE, "FAcebook Object ID");
        if(folder != null && folder.length()>0)
            o.setFolder(folder);
        s.addColumn(o);

        SourceColumn dt = new SourceColumn("date", SourceColumn.LDM_TYPE_DATE, "date");
        dt.setFormat(Constants.DEFAULT_DATE_FMT_STRING);
        if(folder != null && folder.length()>0)
            dt.setFolder(folder);
        s.addColumn(dt);

        SourceColumn metric = new SourceColumn("metric", SourceColumn.LDM_TYPE_ATTRIBUTE, "metric");
        if(folder != null && folder.length()>0)
            metric.setFolder(folder);
        s.addColumn(metric);

        SourceColumn value = new SourceColumn("value", SourceColumn.LDM_TYPE_FACT, "fact");
        if(folder != null && folder.length()>0)
            value.setFolder(folder);
        s.addColumn(value);

        s.writeConfig(new File(configFileName));
        l.debug("Saved Facebook Insights config template.");
    }



    private String convertUnixTimeToString(String value) {
        DateTime dt;
        try {
            long l = Long.parseLong(value);
            dt = new DateTime(l*1000);
        }
        catch (NumberFormatException e) {
            dt = base;
        }
        return baseFmt.print(dt);
    }


    private String fetchData(String uri) {
        try {
            GetMethod method = new GetMethod(uri);
            client.executeMethod(method);
            if (method.getStatusCode() == HttpStatus.SC_OK) {
                return method.getResponseBodyAsString();
            } else {
                String msg = method.getStatusCode() + " " + method.getStatusText();
                l.debug("Exception executing " + method.getName() + " on " + method.getPath() + ": " + msg);
                throw new HttpMethodException("Exception executing " + method.getName() + " on " + method.getPath() + ": " + msg);
            }
        } catch (HttpException e) {
            l.debug("Error invoking Facebook REST API.",e);
            throw new HttpMethodException("Error invoking Facebook REST API.",e);
        } catch (IOException e) {
            l.debug("Error invoking GoodData REST API.",e);
            throw new HttpMethodException("Error invoking Facebook REST API.",e);
        }
    }

    private JSONObject fetchJSON(String uri) {
        return JSONObject.fromObject(fetchData(uri));
    }

    private String constructInsightsApiUrl(String baseUrlWithTime) {
        return baseUrlWithTime + "&access_token="+ URLEncoder.encode(oauthToken);
    }

    private String constructInsightsApiUrl(String baseUrl, DateTime startDate, DateTime endDate) {
        return baseUrl + "?since=" + (startDate.getMillis()/1000) + "&until=" + (endDate.getMillis()/1000) + "&access_token="+URLEncoder.encode(oauthToken);
    }

    private final DateTimeFormatter isoFmt = ISODateTimeFormat.dateTimeParser();
    private final DateTimeFormatter defFmt = DateTimeFormat.forPattern(Constants.DEFAULT_DATE_FMT_STRING);

    private class InsightsRecord {

        private DateTime date;
        private String objectId;
        private String metric;
        private Number value;

        public InsightsRecord(String o, DateTime d, String m, Number v) {
            setObjectId(o);
            setDate(d);
            setMetric(m);
            setValue(v);
        }

        public InsightsRecord(String o, String d, String m, Number v) {
            setObjectId(o);
            setDate(isoFmt.parseDateTime(d));
            setMetric(m);
            setValue(v);
        }

        public InsightsRecord(String o, DateTime d, String m, String v) {
            setObjectId(o);
            setDate(d);
            setMetric(m);
            try {
                Number n = Double.parseDouble(v);
                setValue(n);
            }
            catch (NumberFormatException e) {
                l.debug("Invalid Facebook Insights value: "+v);
            }
        }

        public InsightsRecord(String o, String d, String m, String v) {
            setObjectId(o);
            setDate(isoFmt.parseDateTime(d));
            setMetric(m);
            try {
                Number n = Double.parseDouble(v);
                setValue(n);
            }
            catch (NumberFormatException e) {
                l.debug("Invalid Facebook Insights value: "+v);
            }
        }

        public String[] getRecord()  {
            return new String[] {getObjectId(), defFmt.print(getDate()), getMetric(), (getValue()!=null)?(getValue().toString()):("0")};
        }

        public DateTime getDate() {
            return date;
        }

        public void setDate(DateTime date) {
            this.date = date;
        }

        public String getMetric() {
            return metric;
        }

        public void setMetric(String metric) {
            this.metric = metric;
        }

        public Number getValue() {
            return value;
        }

        public void setValue(Number value) {
            this.value = value;
        }

        public String getObjectId() {
            return objectId;
        }

        public void setObjectId(String objectId) {
            this.objectId = objectId;
        }


    }

    private String fetchInsightsRecords(String uri, List<InsightsRecord> ret) {
        String nextUri = null;
        JSONObject data = fetchJSON(uri);
        if(data != null) {
            JSONArray dt = data.getJSONArray("data");
            if(dt != null && !dt.isEmpty() && dt.size() > 0) {
                for(int i=0; i<dt.size(); i++) {
                    JSONObject metricData = dt.getJSONObject(i);
                    if(metricData != null && !metricData.isNullObject() && !metricData.isEmpty()) {
                        String id = metricData.getString("id");
                        String metricName = metricData.getString("name");
                        if(id != null && id.length()>0 && metricName != null && metricName.length()>0) {
                            if(id.indexOf("/")>0) {
                                String oid = id.split("/")[0];
                                JSONArray dataPoints = metricData.getJSONArray("values");
                                if(dataPoints != null && !dataPoints.isEmpty() && dataPoints.size() > 0) {
                                    for(int j=0; j<dataPoints.size(); j++) {
                                        JSONObject dataPoint = dataPoints.getJSONObject(j);
                                        if(dataPoint != null && !dataPoint.isNullObject() && !dataPoint.isEmpty()) {
                                            String d = dataPoint.getString("end_time");
                                            Object v = dataPoint.get("value");
                                            if(d!=null && v!=null && d.length()>0) {
                                                String value = "0";
                                                if(v instanceof Number || v instanceof String) {
                                                    value = v.toString();
                                                }
                                                else if (v instanceof JSONArray) {
                                                    Object vl = ((JSONArray)v).get(0);
                                                    if(vl != null) {
                                                        value = vl.toString();
                                                    }
                                                }
                                                else {
                                                    value = v.toString();
                                                }
                                                ret.add(new InsightsRecord(oid, d,metricName,value));
                                            }
                                        }
                                    }
                                }
                            }
                            else {
                                l.debug("Invalid format of the Facebook Insights id: "+id);
                            }
                        }
                        else {
                            l.debug("Invalid format of the Facebook Insights id: "+id+ " or metric: "+metricName);
                        }
                    }
                }
            }
            JSONObject paging = data.getJSONObject("paging");
            if(paging != null && !paging.isNullObject() && !paging.isEmpty()) {
                nextUri = paging.getString("next");
            }
        }
        return nextUri;
    }

    /**
     * {@inheritDoc}
     */
    public void extract(String dir) throws IOException {
        File dataFile = new File(dir + System.getProperty("file.separator") + "data.csv");
        extract(dataFile.getAbsolutePath(), true);
    }

    /**
     * {@inheritDoc}
     */
    public void dump(String file) throws IOException {
        extract(file, false);
    }

    /**
     * Extract rows
     * @param file name of the target file
     * @param extendDates add date/time facts
     * @throws IOException
     */
    public void extract(String file, final boolean extendDates) throws IOException {
        File dataFile = new File(file);

        // Is there an IDENTITY connection point?
        final int identityColumn = schema.getIdentityColumn();
        final List<SourceColumn> columns = schema.getColumns();

        l.debug("Extracting Facebook data to file="+dataFile.getAbsolutePath());
        CSVWriter cw = FileUtil.createUtf8CsvEscapingWriter(dataFile);
        String[] header = this.populateCsvHeaderFromSchema(schema);

        // add the extra date headers
        final DateColumnsExtender dateExt = new DateColumnsExtender(schema);
        if(extendDates)
            header = dateExt.extendHeader(header);

        cw.writeNext(header);

        String url = constructInsightsApiUrl(getBaseUrl(), getStartDate(), getEndDate());
        List<InsightsRecord> result = new ArrayList<InsightsRecord>();
        url = fetchInsightsRecords(url, result);
        int cnt = 0;

        while(result != null && result.size() > 0) {
            cnt += result.size();
            l.debug("Started retrieving Facebook data.");
            for(InsightsRecord o : result) {
                String[] record = o.getRecord();
                String[] row;
                String key = "";
                List<String> rowL = new ArrayList<String>(record.length+1);
                for(int i=0; i< record.length; i++) {
                    String value = record[i];
                    if(identityColumn>=0) {
                        int adjustedConfigIndex = (i >= identityColumn) ? (i+1) : (i);
                        if(SourceColumn.LDM_TYPE_ATTRIBUTE.equalsIgnoreCase(columns.get(adjustedConfigIndex).getLdmType()) ||
                           SourceColumn.LDM_TYPE_DATE.equalsIgnoreCase(columns.get(adjustedConfigIndex).getLdmType()) ||
                           SourceColumn.LDM_TYPE_REFERENCE.equalsIgnoreCase(columns.get(adjustedConfigIndex).getLdmType())
                        ) {
                            key += value + "|";
                        }
                        if(SourceColumn.LDM_TYPE_DATE.equalsIgnoreCase(columns.get(adjustedConfigIndex).getLdmType()) &&
                        Constants.UNIX_DATE_FORMAT.equalsIgnoreCase(columns.get(adjustedConfigIndex).getFormat())) {
                            value = convertUnixTimeToString(value);
                        }
                    }
                    else {
                        if(SourceColumn.LDM_TYPE_DATE.equalsIgnoreCase(columns.get(i).getLdmType()) &&
                        Constants.UNIX_DATE_FORMAT.equalsIgnoreCase(columns.get(i).getFormat())) {
                            value = convertUnixTimeToString(value);
                        }
                    }
                    rowL.add(value);
                }
                if(identityColumn>=0) {
                    String hex = DigestUtils.md5Hex(key);
                    rowL.add(identityColumn,hex);
                }
                row = rowL.toArray(new String[]{});
                // add the extra date columns
                if(extendDates)
                    row = dateExt.extendRow(row);
                cw.writeNext(row);
            }
            result.clear();
            url = fetchInsightsRecords(url, result);
        }

        l.debug("Retrieved " + cnt + " rows of Facebook data.");
        cw.flush();
        cw.close();
        l.debug("Extracted Facebook data.");
    }

    /**
     * {@inheritDoc}
     */
    public boolean processCommand(Command c, CliParams cli, ProcessingContext ctx) throws ProcessingException {
        try {
            if(c.match("GenerateFacebookInsightsConfig")) {
                generateConfig(c, cli, ctx);
            }
            else if(c.match("LoadFacebookInsights") || c.match("UseFacebookInsights")) {
                load(c, cli, ctx);
            }
            else
                return super.processCommand(c, cli, ctx);
        }
        catch (IOException e) {
            throw new ProcessingException(e);
        }
        return true;
    }

    /**
     * Loads new Facebook file command processor
     * @param c command
     * @param p command line arguments
     * @param ctx current processing context
     * @throws java.io.IOException in case of IO issues
     */
    private void load(Command c, CliParams p, ProcessingContext ctx) throws IOException {

        String configFile = c.getParamMandatory("configFile");
        setBaseUrl(c.getParamMandatory("baseUrl"));
        setStartDate(defFmt.parseDateTime(c.getParamMandatory("startDate")));
        setEndDate(defFmt.parseDateTime(c.getParamMandatory("endDate")));
        String auth = c.getParamMandatory("authToken");
        File conf = FileUtil.getFile(configFile);
        initSchema(conf.getAbsolutePath());
        setOauthToken(auth);
        // sets the current connector
        ctx.setConnector(this);
        setProjectId(ctx);
        l.info("Facebook Insights Connector successfully loaded.");
    }

    /**
     * Generate new config file from CSV command processor
     * @param c command
     * @param p command line arguments
     * @param ctx current processing context
     * @throws java.io.IOException in case of IO issues
     */
    private void generateConfig(Command c, CliParams p, ProcessingContext ctx) throws IOException {
        String name = c.getParamMandatory("name");
        String configFile = c.getParamMandatory("configFile");
    	String folder = c.getParam( "folder");
        FacebookInsightsConnector.saveConfigTemplate(name, configFile, folder);
        l.info("Facebook Insights Connector configuration successfully generated. See config file: "+configFile);
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(DateTime startDate) {
        this.startDate = startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(DateTime endDate) {
        this.endDate = endDate;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

}