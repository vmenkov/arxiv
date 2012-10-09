<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>

<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="edu.rutgers.axs.recommender.*" %>
<%@ page import="edu.rutgers.axs.html.*" %>

<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewFolder main=new ViewFolder(request, response, true);
   User actor = main.actor;
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html>
<head>
<meta name="Description" content="List of articles in the user's personal folder"/>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Personal folder for user <%= main.actorUserName %></title>

<link rel="stylesheet" type="text/css" href="../_technical/styles/styles_all.css" />
<!--[if lte IE6]>
<link rel="stylesheet" type="text/css" href="../_technical/styles/styles_ie.css" /><![endif]-->
<link rel="stylesheet" type="text/css" href="../styles/results.css" />
<!--[if IE]>
<link rel="stylesheet" type="text/css" href="../styles/results_ie.css" />
<![endif]-->
<link rel="icon" type="image/x-icon" href="../favicon.ico" />

<script type="text/javascript" src="../_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="../_technical/scripts/jquery-transitions.js"></script>
<script type="text/javascript" src="../scripts/blur.js"></script>
<script type="text/javascript" src="../scripts/buttons_control.js"></script>



</head>
<body>

<h1>Personal folder for user <em><%= main.actorUserName %></em>
</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<p>

<P>Your personal folder contains the following <%=main.list.size()%> articles.</p>

<%

	for(int i=0; i<main.list.size(); i++) {
	Action a =  main.list.elementAt(i);
	ArticleEntry e = main.entries.elementAt(i);
%>
<%= main.resultsDivHTML(e) %>	



 <%  }  %>
</p>


<hr> <p>System message: <%= main.infomsg%> </p> <% } %>

<icd:RU/>

</body>
</html>