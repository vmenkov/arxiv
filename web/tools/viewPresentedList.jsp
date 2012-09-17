<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
//   ViewPresentedList main=new ViewPresentedList(request,response);
   ViewObject main=new ViewObject(request,response, PresentedList.class);
   PresentedList li = (PresentedList)main.li;
%>

<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>Presented list
</title>
</head>
<body>


<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<h1>Error</h1>
<%   } else {      %>
<p>
<h1>Presented list no. <em><%= li.getId() %></em></h1>

<h2>Summary</h2>
<p>
<table border=1>
<%= Reflect.htmlHeaderRow(PresentedList.class, true) %>
<%= Reflect.htmlRow(li, true) %>
</table></p>

<h2>Content of the list</h2>

<p>This is the list of articles that that was actually shown to the user.</p>

<p>
<% if (li.getDocs()==null) { %> No entries. <% } else { %>
<table border=1>
 	<%= Reflect.htmlHeaderRow(PresentedListEntry.class, true) %>
<%		
	for(int i=0; i<li.getDocs().size(); i++) {
%>
	<%= Reflect.htmlRow(li.getDocs().elementAt(i), true) %>
 <%  }  
%>
</table>
<%}%>
</p>

<hr> <p>System message: <%= main.infomsg%> </p> <% } %>

<icd:RU/>

</body>
</html>