<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% ResultsBase main=new ResultsBase(request,response); %>
<html>
<head>
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Your Account
</title>
<jsp:include page="../include/date-head-include.html" />
</head>
<body>
<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {  
     User u = main.getUserEntry();
    %>


<h2>Your account information</h2>
<p>
<table border=1>
<tr><%= User.header4cells()  %> </tr>
<tr><%= u.to4cells()  %> </tr>
</table>
</p>

<p>
<ul>
<li><a href="editUserFormSelf.jsp">Modify your account information</a>
</ul>
</p>

<h2>Your activity</h2>
<p>
<ul>
<li><a href="viewFolder.jsp">Your personal folder</a>
<li><a href="viewActionsSelf.jsp">Your browsing activity history</a>
<li><a href="viewRankedPages.jsp">Pages ranked based on your actions</a>
<li><a href="viewUserProfile.jsp?expert=true">User profile</a> - weighted list of terms from the articles you've interacted with. (This is directly derived from the entire history).
(This page also has a link to the sugg list based on this profile)
<li><a href="viewUserProfile.jsp?expert=true&mode=TJ_ALGO_2_USER_PROFILE">User profile</a> - the current "iteration" of Algorithm 2 (based on the user profile on which the last Algo 1 suggestion list was generated, with updates based on the user's activity since then.)
(This page also has a link to the sugg list based on this profile)

</ul>
</p>


<h2><a name="plan-2012-06">Paul's new experiment plan, June 2012</a></h2>

These are the building blocks of the experiment as it is currently planned: 

<ul>
<li>Treatment A: <a href="../user_cat_search.jsp?user_cat_search=true">User cat search</a>
<li>Treatment B: to see what Treatment B will be like, go to 
<a href="viewUserProfile.jsp?expert=true&mode=TJ_ALGO_2_USER_PROFILE">Algo 2 user profile</a>, check the radio button at "sublinear utility", and click on the "view suggestions" button

</ul>

<hr>
<p><strong>
Tools below will be mostly moved to the "research" page
</strong></p>

<h2>Paul's old experiment plan, May 2012</h2>
<p>
<ul>
<li><a href="viewSuggestions.jsp?mode=LOG_SUGGESTIONS_1">Treatment A</a>: User profile UP0 (directly based on all user's activity), applied to the entire corpus (log(TF)*IDF)

</ul>
</p>

<h2>Recommendation lists</h2>

<p>
Various algorithms for finding pages possibly interesting to you.
</p>

<p>
<ul>
<li><a href="viewSuggestions.jsp?expert=true&mode=LINEAR_SUGGESTIONS_1">Suggestions - linear model</a> - applying the user profile vector to the entire data set. (Cosine similarity).
<li><a href="viewSuggestions.jsp?expert=true&mode=LINEAR_SUGGESTIONS_1&days=7">Suggestions - linear model - last 7 days</a> - applying the user profile vector to the most recent articles (the last 7 days). (Cosine similarity).
<li><a href="viewSuggestions.jsp?expert=true&mode=TJ_ALGO_1_SUGGESTIONS_1">Suggestions - Thorsten's Algorthm 1</a> - applying Algortithm 1 to rank the entire data set.
<li><a href="viewSuggestions.jsp?expert=true&mode=TJ_ALGO_1_SUGGESTIONS_1&days=7">Suggestions - Thorsten's Algorthm 1 - last 7 days</a> - applying Algortithm 1 to rank the most recent articles (the last 7 days). 

</ul>
</p>


<h2><a name="other">Other</a></h2>

<p>
<ul>
<li><a href="<%=main.cp%>">My.arXiv Main page</a>
<%
if (u.isAdmin() || u.isResearcher()) {
%>
<li><a href="<%=main.cp%>/tools">My.arXiv research tools (staff only)</a>
<%
}
%>
</ul>
</p>




<icd:RU/>
<%   }       %>

</body>
</html>
