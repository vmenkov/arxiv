<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ResultsBase main=new ResultsBase(request,response);
%>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>My.arXiv: database query</title>
</head>
<body>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>

<h1>My.arXiv database query form</h1>

<h2>Data description</h2>

<p>This forms allows you to send a free-form <a href="http://download.oracle.com/docs/cd/E11035_01/kodo41/full/html/ejb3_overview_query.html">JPQL</a> or SQL query to our database. 
</p>

<p>Please refer to the <a href="../manual.html">User Guide</a> for the
high-level overview of the tables existing in the database.</p>

<h2>JPQL vs. SQL</h2>

<p>JPQL can be thought as a sort of "object-oriented" wrapper over
SQL. As far as our application is concerned, the main difference
between the two is the following: when object of one type contains a
reference to an object of another type (e.g., a PhoneCall refers to
the Respondent being called), SQL "talks" in terms of the actual
stored table column (here, respondent_id), while JPQL "talks" in terms
of objects being references.<p>

</p>
To help you get started, below are
equivalent sample queries in the two langauges. Note that table names are case-sensitive.</p>

<p>Hint: you can use the first 3 JPQL queries from the table below to see what the names of all fields in each table are.</p>

<p>
<table border=1>
<tr><th>JPQL <th>SQL <th>comments
<tr><td>select x from User x <td> select x.* from arxiv_users x
<tr><td>select x from Action x <td> select x.* from Action x
<tr><td> -
    <td> select x.* from Action x where x.user_id=1 

<tr>
<td>select x from Action x, User u where x.user=u and u.user_name='vmenkov'
<td>select x.* from Action x, arxiv_users u where x.user_id=u.id and u.user_name='vmenkov'
<tr>
<td>select x from ArticleStats x
<td>select x.* from ArticleStats x
<tr>
<td> -
<td>select m.failed, count(*) from Task m group by m.failed 

<tr>
<td>select x from Task x
<td>select x.* from Task x

<tr>
<td> -
<td> select x.thisFile, x.inputFile, y.inputFile from DataFile x, DataFile y where x.type='TJ_ALGO_1_SUGGESTIONS_1' and y.thisFile=x.inputFile

<tr>
<td>select x.type, x.thisFile, x.inputFile, y.type, y.inputFile from DataFile x, DataFile y where  y.thisFile=x.inputFile
<td>select x.type, x.thisFile, x.inputFile, y.type, y.inputFile from DataFile x, DataFile y where  y.thisFile=x.inputFile

<tr>
<td> -
<td> select le.DF_ID, le.rank, a.aid from ListEntry le, ArticleStats a where le.ASTAT_ID = a.id order by DF_ID, rank;
<td>Viewing suggestion list (as stored in the SQL database)

<tr>
<td>select a.user, count(a) from Action a group by a.user order by count(a) desc 
<td>select u.user_name, u.email, u.id, count(*) from Action a, arxiv_users u where a.USER_ID=u.id group by a.USER_ID  order by count(*) desc
<td>Measuring overall user activity

<!--
<tr><td>select x from Respondent x where x.id = 69 <td> select x.* from Respondent x where x.id = 69
<tr><td>select x from Respondent x where x.id in (69,70,71) <td> select x.* from Respondent x where x.id  in (69,70,71) 
<tr><td>select x from PhoneCall x<td>select x.* from PhoneCall x
<tr><td>select x from Response x<td>select x.* from Response x
<tr><td>select m from Respondent m  where m.id &lt; 20
<td>select m.* from Respondent m  where m.id &lt; 20

<tr><td>select m.id, m.lastName, c from Respondent m, PhoneCall c where m.id &lt; 10 and c.respondent = m 

<tr><td>select a from Response a, PhoneCall c, Respondent m where a.phoneCall = c and c.respondent = m and m.id = 13

<tr><td>select m.last_name, m.id, count(a) from Response a, PhoneCall c, Respondent m where a.phoneCall = c and c.respondent = m group by m   
<td> select  m.last_name, c.respondent_id, count(*) 
from Response a, PhoneCall c, Respondent m  
where a.phoneCall_id = c.id and c.respondent_id = m.id 
group by c.respondent_id
-->
</table>
</p>

<h2>Query form</h2>

<p>This form can be used to submit either JPQL or SQL query. Check the
language radio button under the query box apropriately.</p>

<form method=post action=QueryServlet>
<textarea name="<%=QueryServlet.QUERY%>" rows=20 cols=120>
select x from User x 
</textarea>

<p>
<table border=1>
<tr>
<td>Query language
<td>Format results as
<td>Disposition of results
</tr>
<tr>
<td><%= QueryServlet.mkLanguageSelector() %>
<td><%= QueryServlet.mkFormatSelector() %>
<td><%= QueryServlet.mkDownloadSelector() %>
</tr>
<tr><td colspan=3>
<%= QueryServlet.mkHeadBox() %>
<tr><td colspan=3>
Limit the number of displayed result rows to this number: <input name="<%= QueryServlet.MAX_RESULT %>" size=10>
</table>
</p>

<input type=submit value="SEND">

<input type=reset value="Clear form">

</form>


<icd:RU/>

<% } %>

</body>
</html>