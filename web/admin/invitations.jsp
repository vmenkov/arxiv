<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ListInvitations main=new ListInvitations(request,response);
%>
<html>
<head>
<title>Invitation Manager</title>

</head>
<body>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp"%>
<%   } else {        
%>
<h1>Invitation Manager</h1>
<h2>List of existing invitations</h2>

<h4>Editing invitation</h4>


<p>You can edit any of the existing invitations. The following changes are possible:

<ul>

<li>You can change the  user number limit or the expiration date. 

<li>You can close an open invitation. It will stay closed until explicitly re-opened.

<li> You can open a closed invitation. If doing so, make sure to
adjust the expiration date and/or the user number limit as
necessary. Otherwise, if the user number limit has been reached, or
the expiration date has passed, the expiration will soon automatically
close again.  
</ul>
</p>

<h4>How to invite users</h4>

<p> The blue links in the "code" column are links to the registration
page that prospective users must visit to register pursuant to the
respective invitation. You can send that URL to prospective users by
email.  </p>

<p>
<table border="1">
<tr>
<%= Reflect.htmlHeaderRow(Invitation.class, false) %>
<th valign="top">Users registered</th>
<th valign="top">Edit</th>
</tr>
<% for(int i=0; i<main.list.size(); i++) {
   Invitation inv = main.list.elementAt(i);
   String bgColor = inv.getOpen()? "#E0FFE0" :  "#FFE0E0";
%>
<tr style="background:<%=bgColor%>;">
<%= Reflect.htmlRow(inv, false, true) %>
<td>
<%= main.userCnt(inv.getId()) %>
</td>
<td>
<a href="editInvitationForm.jsp?id=<%=inv.getId()%>">Edit</a>
</td>
</tr>
<% } %>
</table>
</p>


<h2>Creating a new invitation</h2>
<ul>
<li><a href="editInvitationForm.jsp">Create a new invitation</a>
</ul>

<% } %>

<hr>
<div><small>System messages:<pre>
<%= main.infomsg%>
</pre>
</small></div>

<icd:RU/>

</body>
</html>