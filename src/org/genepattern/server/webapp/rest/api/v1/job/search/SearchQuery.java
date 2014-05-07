package org.genepattern.server.webapp.rest.api.v1.job.search;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import com.google.common.base.Strings;
import org.apache.log4j.Logger;
import org.genepattern.server.config.GpContext;
import org.genepattern.server.webapp.rest.api.v1.Rel;
import org.genepattern.server.webservice.server.Analysis.JobSortOrder;

/**
 * Representation of the search query.
 * Also use this to generate page links.
 * 
 * @author pcarr
 */
public class SearchQuery {
    private static final Logger log = Logger.getLogger(SearchQuery.class);

    public static final int DEFAULT_PAGE_SIZE=20;
    public static final String Q_USER_ID="userId";
    public static final String Q_GROUP_ID="groupId";
    public static final String Q_BATCH_ID="batchId";
    public static final String Q_PAGE="page";
    public static final String Q_PAGE_SIZE="pageSize";
    public static final String Q_ORDER_BY="orderBy";
    public static final String Q_ORDER_FILES_BY="orderFilesBy";

    /**
     * the full URI to the jobs resource, for constructing links to related resources
     */
    final String jobsResourcePath;
    /**
     * the GP User who is making the query
     */
    final String currentUser;
    /**
     * is the current user an admin
     */
    final boolean currentUserIsAdmin;
    /**
     * search for jobs owned by this user, can be '*' to indicate all jobs.
     */
    final String userId;

    final String groupId;
    final String batchId;
    
    final int page;
    final int pageSize;
    
    final String orderBy;
    final String orderFilesBy;
    final JobSortOrder jobSortOrder;
    final boolean ascending;

    private SearchQuery(final Builder in) {
        this.jobsResourcePath=in.jobsResourcePath;
        this.currentUser=in.userContext.getUserId();
        this.currentUserIsAdmin=in.userContext.isAdmin();
        // filters
        this.userId=in.userId;
        this.groupId=in.selectedGroup;
        this.batchId=in.selectedBatchId;
        // pages
        this.page=in.pageNum;
        this.pageSize=in.pageSize;
        // order
        this.orderBy=in.orderBy;
        this.jobSortOrder=getJobSortOrder(in.orderBy);
        this.ascending=isAscending(in.orderBy);
        this.orderFilesBy=in.orderFilesBy;
    }
    
    /**
     * orderBy=jobId | taskName | dateSubmitted | dateCompleted | status
     * @param orderBy
     * @return
     */
    private JobSortOrder getJobSortOrder(final String orderBy) {
        if (orderBy==null) {
            return JobSortOrder.JOB_NUMBER;
        }
        final String jobSortColumn;
        if (orderBy.startsWith("+") || orderBy.startsWith("-")) {
            jobSortColumn=orderBy.substring(1);
        }
        else {
            jobSortColumn=orderBy;
        }
        if ("jobId".equals(jobSortColumn)) {
            return JobSortOrder.JOB_NUMBER;
        }
        else if ("taskName".equals(jobSortColumn)) {
            return JobSortOrder.MODULE_NAME;
        }
        else if ("dateSubmitted".equals(jobSortColumn)) {
            return JobSortOrder.SUBMITTED_DATE;
        }
        else if ("dateCompleted".equals(jobSortColumn)) {
            return JobSortOrder.COMPLETED_DATE;
        }
        else if ("status".equals(jobSortColumn)) { return JobSortOrder.JOB_STATUS; }
        return JobSortOrder.JOB_NUMBER;
    }
    
    private boolean isAscending(final String orderBy) {
        if (orderBy==null) {
            // by default, ascending
            return true; 
        }
        if (orderBy.startsWith("+")) {
            return true;
        }
        if (orderBy.startsWith("-")) {
            return false;
        }
        return true;
    }
    

    public String getCurrentUser() {
        return currentUser;
    }
    public boolean isCurrentUserAdmin() {
        return currentUserIsAdmin;
    }
    public boolean isShowEveryonesJobs() {
        return "*".equals(userId);
    }
    public String getSelectedGroup() {
        return groupId;
    }
    public String getBatchId() {
        return batchId;
    }
    public boolean isBatch() {
        return !Strings.isNullOrEmpty(batchId);
    }
    public boolean isGroup() {
        return !Strings.isNullOrEmpty(groupId);
    }

    public JobSortOrder getJobSortOrder() {
        return this.jobSortOrder;
    }
    public boolean isAscending() {
        return ascending;
    }

    public int getPageNum() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    // for the BATCH JOB QUERY, compute maxResults and firstResult from the pageSize and pageNum attributes
    public int getFirstResult() {
        return pageSize * (page-1);
    }

    /**
     * Creates a link to a new page, using all other search criteria.
     * @param page
     * @return
     */
    public PageLink makePageLink(final String name, final int toPage) {
        return makePageLink(null, name, toPage);
    }
    public PageLink makePageLink(final Rel rel, final String name, final int toPage) {
        PageLink pageLink=new PageLink(toPage);
        pageLink.rel=rel;
        pageLink.name=name;
        try {
            final String queryString=getQueryString(toPage);
            if (queryString!=null) {
                pageLink.href=jobsResourcePath+"?"+queryString;
            }
            else {
                pageLink.href=jobsResourcePath;
            }
        }
        catch (UnsupportedEncodingException e) {
            log.error("Error creating link toPage="+toPage, e);
            pageLink.href=jobsResourcePath;
        }
        return pageLink;
    }

    /**
     * Example, <pre>
     *     userId=&groupId=&batchId=&page=&pageSize=
     * </pre>
     * @return the http query string for the search.
     */
    private String getQueryString(int toPage)  throws UnsupportedEncodingException {
        QueryStringBuilder b=new QueryStringBuilder();
        b.param(Q_USER_ID, userId);
        b.param(Q_GROUP_ID, groupId);
        b.param(Q_BATCH_ID, batchId);
        b.param(Q_PAGE, ""+toPage);
        if (orderBy != null) {
            b.param(Q_ORDER_BY, orderBy);
        }
        if (orderFilesBy != null) {
            b.param(Q_ORDER_FILES_BY, orderFilesBy);
        }
        return b.build();
    }
    //        private String getQueryString_Jersey() {
    //            UriBuilder b=UriBuilder.fromPath("");
    //            if (userId!=null) {
    //                b=b.queryParam(Q_USER_ID, userId);
    //            }
    //            final URI uri=b.build();
    //            String queryString=uri.getRawQuery();
    //            return queryString;
    //        }

    public static class QueryStringBuilder {
        private static class QueryParam {
            private final String param;

            //public QueryParam(final String name) throws UnsupportedEncodingException {
            //    this(name, (String)null);
            //}
            public QueryParam(final String name, final String value) throws UnsupportedEncodingException {
                final String encodedName=URLEncoder.encode(name, "UTF-8");
                if (value!=null) {
                    final String encodedValue=URLEncoder.encode(value, "UTF-8");
                    param=encodedName+"="+encodedValue;
                }
                else {
                    param=encodedName;
                }
            }
            public String toString() {
                return param;
            }
        }

        private List<QueryParam> params;

        public QueryStringBuilder param(final String name) throws UnsupportedEncodingException  {
            return param(name, null);
        }
        public QueryStringBuilder param(final String name, final String value) throws UnsupportedEncodingException {
            //skip null values
            if (value==null) {
                return this;
            }
            if (params==null) {
                params=new ArrayList<QueryParam>();
            }
            
            params.add(new QueryParam(name, value));
            return this;
        }

        public String build() {
            //null means, no query string
            if (params==null || params.size()==0) {
                return null;
            }
            boolean first=true;
            final StringBuffer sb=new StringBuffer();
            for(final QueryParam param : params) {
                if (first) {
                    first=false;
                }
                else {
                    sb.append("&");
                }
                sb.append(param.toString());
            }
            return sb.toString();
        }
    }

    public static class Builder {
        private final String jobsResourcePath;
        private final GpContext userContext;
        private String userId=null; // null or not-set means, currentUser
        private String selectedGroup=null;
        private String selectedBatchId=null;
        private String orderBy=null;
        private String orderFilesBy=null;
        private int pageNum=1;
        private int pageSize=DEFAULT_PAGE_SIZE;

        public Builder(final String jobsResourcePath, final GpContext userContext) {
            this.jobsResourcePath=jobsResourcePath;
            this.userContext=userContext;
        }

        public Builder userId(final String userId) {
            this.userId=userId;
            return this;
        }

        public Builder groupId(final String groupId) {
            this.selectedGroup=groupId;
            return this;
        }

        public Builder batchId(final String batchId) {
            this.selectedBatchId=batchId;
            return this;
        }

        public Builder pageNum(final int pageNum) {
            this.pageNum=pageNum;
            return this;
        }

        public Builder pageSize(final int pageSize) {
            this.pageSize=pageSize;
            return this;
        }
        
        public Builder orderBy(final String orderBy) {
            this.orderBy=orderBy;
            return this;
        }
        public Builder orderFilesBy(final String orderFilesBy) {
            this.orderFilesBy=orderFilesBy;
            return this;
        }

        public SearchQuery build() {
            //special-case: if necessary initialize the pageSize
            if (pageSize<=0) {
                initPageSize();
            } 
            return new SearchQuery(this);
        }

        private void initPageSize() {
            //TODO: init from DB
            log.error("initPageSize not implemented, using hard-coded value: 20");
            this.pageSize=20;
        }
    }
}
