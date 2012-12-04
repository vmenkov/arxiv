<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   EditInvitationForm main=new EditInvitationForm(request,response);
%>
<html>
<head>
<title>Creating or editing an invitation</title>

<!-- jQuery -->
<script type="text/javascript" src="../date/scripts/jquery.min.js"></script>

<!-- required plugins -->
<script type="text/javascript" src="../date/scripts/date.js"></script>

<!-- jquery.datePicker.js -->
<script type="text/javascript" src="../date/scripts/jquery.datePicker.js"></script>
  
<!-- datePicker required styles -->
<link rel="stylesheet" type="text/css" media="screen" href="../date/styles/datePicker.css">

<!-- page specific styles -->
<link rel="stylesheet" type="text/css" media="screen" href="../date/styles/calendar.css">
</head>
<body>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp"%>

<p>
To try again, please go back using the "Back" button in your web browser,
and reenter correct data.
</p>

<%   } else {        
%>

<% if (main.id<=0) { %>
<h1>Creating a new invitation</h1>
<% } else { %>
<h1>Editing invitation no.  <em><%=main.id%></em></h1>

<p>So far, <%= main.userCnt %> users have responded to this invitation and successfully created accounts.</p>

<p>
<table border="1">
<tr>
<td><%= main.o.reflectToString()  %></td>	
</tr>
</table>
</p>

<% } %>

<p>If you want to open an invitation, make sure to set an appropriate
max number of users and an adequate expiration date, and set "open" to "true".
</p>

<form method=post action="editInvitation.jsp">
<%= Tools.inputHidden(EditInvitation.ID, main.id) %> <br>

<p>
<table><tr><td>
<%= main.entryForm()%>
</td><td>
<!--space for the pop-up date selector -->
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
</td></tr></table>

</p>

<p>
<input type="submit" value="Submit">
</p>

<p>
Back to the <a href="invitations.jsp">Invitation Manager</a>
</p>

<% } %>

<icd:RU/>

</body>
</html>