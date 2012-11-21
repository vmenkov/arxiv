<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% ResultsBase main=new ResultsBase(request,response); %>
<html>
<head>
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<link rel="stylesheet" type="text/css" href="../_technical/styles/styles_all.css" />
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
<li><a href="editUserFormSelf.jsp">Modify your account information</a> - here you can change various settings that control what articles are recommended to you</li>
</ul>
</p>

<h2>Your activity</h2>
<p>
<ul>
<li><a href="viewFolder.jsp">Your personal folder</a>
<li><a href="viewActionsSelfDetailed.jsp">Your browsing activity history</a> - detailed
<li><a href="viewActionsSelf.jsp">Your browsing activity history</a> - raw
<li><a href="viewRankedPages.jsp">Articles ranked based on your actions</a>
<li><a href="viewUserProfile.jsp?mode=TJ_ALGO_2_USER_PROFILE">User profile</a> - the current "iteration" of Algorithm 2 (based on the user profile on which the last Algo 1 suggestion list was generated, with updates based on the user's activity since then.)
(This page also has a link to the sugg list based on this profile)
<li><a href="viewUserProfile.jsp?mode=USER_PROFILE"><!--?expert=false-->User profile</a> - weighted list of terms from the articles you've interacted with. (This is directly derived from the entire history; it is <em>not</em> currently used for the main-page suggestion list.)

</ul>
</p>

<!--
<h2>Recommendation lists</h2>

<p>
Various algorithms for finding articles possibly interesting to you.
</p>

<p>
<ul>
<li><a href="viewSuggestions.jsp?expert=true&mode=LINEAR_SUGGESTIONS_1&days=0">Suggestions - linear model</a> - applying the user profile vector to the entire data set. (Cosine similarity).
<li><a href="viewSuggestions.jsp?expert=true&mode=LINEAR_SUGGESTIONS_1&days=7">Suggestions - linear model - last 7 days</a> - applying the user profile vector to the most recent articles (the last 7 days). (Cosine similarity).
<li><a href="viewSuggestions.jsp?expert=true&mode=TJ_ALGO_1_SUGGESTIONS_1&days=0">Suggestions - Thorsten's Algorthm 1</a> - applying Algortithm 1 to rank the entire data set.
<li><a href="viewSuggestions.jsp?expert=true&mode=TJ_ALGO_1_SUGGESTIONS_1&days=7">Suggestions - Thorsten's Algorthm 1 - last 7 days</a> - applying Algortithm 1 to rank the most recent articles (the last 7 days). 

</ul>
</p>
-->


<h2><a name="other">Other</a></h2>

<p>
<ul>
<li><a href="<%=main.cp%>">My.arXiv Main page</a>
<%
if (u.isAdmin() || u.isResearcher()) {
%>
<li><span class="researcher"><a href="<%=main.cp%>/tools">My.arXiv research tools (staff only)</a></span></li>
<%
}
%>
</ul>
</p>


<icd:RU/>
<%   }       %>

</body>
</html>

