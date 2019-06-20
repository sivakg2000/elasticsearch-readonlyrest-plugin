/*
 *    This file is part of ReadonlyREST.
 *
 *    ReadonlyREST is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    ReadonlyREST is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with ReadonlyREST.  If not, see http://www.gnu.org/licenses/
 */
package tech.beshu.ror.es;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.SearchRequest;

/**
 *
 * @author Siva Karuppiah
 */
public class MyUtils {

    private Pattern regex = Pattern.compile("\\\"from\\\"\\s+\\:\\s+(\\d+)\\,\\n\\s+\\\"to\\\"\\s+\\:\\s+(\\d+)");
    private Logger logger;

    public MyUtils(Logger logger) {
        this.logger = logger;
    }

    public String[] getTime(String q) {
        Matcher ipRegexMatch = regex.matcher(q);
        if (ipRegexMatch.find()) {
            String[] rVal = new String[2];
            rVal[0] = ipRegexMatch.group(1);
            rVal[1] = ipRegexMatch.group(2);
            return rVal;
        }
        return null;
    }

    public String[] getUpdatedIndices(String[] indices, String rangeQuery) {
        String[] rVal = getTime(rangeQuery);
        boolean isAdmin = false;
        if (rVal != null && rVal.length == 2) {
            List<String> dateList = getDates(rVal[0], rVal[1]);

            List<String> filterList = new ArrayList<>();

            for (String index : indices) {

                if (!index.contains("*")) {
                    String indexDate = index.substring(index.length() - 10);

                    if (dateList.contains(indexDate)) {
                        filterList.add(index);
                    }
                } else {
                    isAdmin = true;
                }
            }

            if (filterList.isEmpty()) {
                if (indices.length > 0) {
                    filterList.add(indices[indices.length - 1]);

                    logger.info("D Indice  [U->" + filterList + "]");
                    //filterList.add("perimeter-s-1031-2019-01-09"); 
                }
            }

            logger.debug("Indices [R->" + Arrays.toString(indices) + ", U->" + filterList + ",D->" + getDateRange(dateList) + "]");

            logger.info("Indices Size [R->" + indices.length + ", U->" + filterList.size() + ",D->" + getDateRange(dateList) + "]");
            if (isAdmin) {
                logger.info("Admin Access, Indices [R->" + Arrays.toString(indices) + "]");
                return indices;
            }

            return filterList.toArray(new String[0]);

        } else {
            return indices;
        }

    }

    private String getDateRange(List<String> dList) {
        String rVal = "";
        if (!dList.isEmpty()) {
            Collections.sort(dList);
            if (dList.size() == 1) {
                rVal = "[S->" + dList.get(0) + " , E->" + dList.get(0) + "]";
            } else {
                rVal = "[S->" + dList.get(0) + " , E->" + dList.get(dList.size() - 1) + "]";
            }
        } else {
            rVal = "[]";
        }

        return rVal;
    }

    public void updatedSearchRequest(SearchRequest request) {

        try {

            String qVal = request.source().query().toString();

            //newReq.indices(bUtil.getUpdatedIndices(newReq.indices(), qVal));
            String[] newIndices = getUpdatedIndices(request.indices(), qVal);
            request.indices(newIndices);

        } catch (NullPointerException ex) {

        }

    }

    public List<String> getDates(String str_date, String end_date) {
        // TODO Auto-generated method stub
        List<Date> dates = new ArrayList<>();
        List<String> dateStrVal = new ArrayList<>();

        //String str_date = "27/08/2010";
        //String end_date = "02/09/2010";
        DateFormat formatter;

        //2019-01-30
        formatter = new SimpleDateFormat("yyyy-MM-dd");

        long interval = 24 * 1000 * 60 * 60; // 1 hour in millis
        long endTime = Long.parseLong(end_date); // create your endtime here, possibly using Calendar or Date
        long curTime = Long.parseLong(str_date);
        while (curTime <= endTime) {
            dates.add(new Date(curTime));
            curTime += interval;
        }
        for (int i = 0; i < dates.size(); i++) {
            Date lDate = (Date) dates.get(i);
            String ds = formatter.format(lDate);
            dateStrVal.add(ds);
            //System.out.println(" Date is ..." + ds);
        }

        Collections.sort(dateStrVal);
        return dateStrVal;

    }

    /*private List<LocalDate> getDates(
            String sDate, String eDate) {

        LocalDate startDate
                = Instant.ofEpochMilli(Long.parseLong(sDate)).atZone(ZoneId.systemDefault()).toLocalDate();

        LocalDate endDate
                = Instant.ofEpochMilli(Long.parseLong(eDate)).atZone(ZoneId.systemDefault()).toLocalDate();

        long numOfDaysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (numOfDaysBetween == 0) {
            numOfDaysBetween = 1;
        }
        return IntStream.iterate(0, i -> i + 1)
                .limit(numOfDaysBetween)
                .mapToObj(i -> startDate.plusDays(i))
                .collect(Collectors.toList());
    }*/
}
