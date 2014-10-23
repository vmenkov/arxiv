<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<%
   UploadPapers main=new UploadPapers(request,response);
%>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<% if (main.wantReload) { %>
<meta http-equiv="refresh" content="2; url=<%=main.reloadURL%>"/>
<%}%>
<title>Uploading personal papers</title>
<jsp:include page="../include/date-head-include.html" />
</head>
<body>

<% 
   if (main.error) {   %>  <%@include file="../include/error.jsp" %>


<%   } else if (main.check) {      %>
<h1><%= main.checkTitle %></h1>
<pre>
<%= main.checkText %>
</pre>

<hr>
<p>
<% if (main.wantReload) { %>
This page should automatically refresh within a few seconds. 
If it does not, click the button below to continue:
<%}%>
</p>

<form action="<%=main.reloadURL%>">
<input type=submit value="Continue">
</form>
     
<%   } else {    %>

<h1>Uploading personal papers</h1>


     <%if (main.uploadCnt>0) { %>

     <h2>Papers uploaded</h2>

     <p>
     <%= main.uploadCnt%> PDF document(s) uploaded now.
     </p>

     <% }%>

<h2>Summary of your uploading activity so far</h2>
<%= main.dirInfo(false) %>

<p>
<%= main.dirInfo(true) %>

<h2>Uploading personal papers</h2>
     
<p>To help My.ArXiv personalize your user experience, you can upload a
few papers, in PDF format, that have been written by you, or are of
particular interest to you. (For example, those may be a few papers
from your personal "to read" list - they can even be some papers from
arxiv.org!) Our system will try to analyze their content in order to be able
to find recent ArXiv papers that may be of interest to you.

<p>You can upload any number of papers at this time. (Once you upload
one paper, the system will allow you to upload one more, etc.) 

<p>Note that after all papers of your choice have been uploaded, and
your initial recommendation list has been created and displayed, you
will not be allowed to upload more papers.

<p>There are four options for uploading a paper: 

<ul> 

<li>If the paper is on the web somewhere (including on arxiv.org!) you can
simply provide a URL for the PDF file.

<li>You can simply upload the PDF file.

<li>You can upload an HTML document that contains links to several PDF
documents (which themselves have to be on the web somewhere).

<li>You can provide a URL of an HTML document that contains links to several PDF
documents 

</ul>

<p>Note that if you upload multiple PDF documents, they all have to have different names.

<table border=1>
<tr> <td>
Option 1 - specify a URL for a PDF file, or an HTML documents with links to PDF documents,
e.g. <tt>http://www.example.com/some-artcle.pdf</tt> or
<tt>http://my.domain.edu/my-home-page/cv.html</tt>
<form  action="uploadPapers.jsp" method="post"><br>
URL: <input type="text" size="80" name="url">
<input type="submit" value="Load PDF or HTML from this URL" />
</form>
</tr>

<tr> <td>
Option 2 - upload a PDF file or an HTML file with links to PDF documents:  <br>
<form  enctype="multipart/form-data" action="uploadPapers.jsp" method="post">
<input type="file" size="80" name="pdf">
<input type="submit" value="Upload PDF or HTML file" />
</form>
</tr>



</table>


<%   }      %>


<!--
<ul>
<li><a href="<%=main.cp%>">Main page</a>
<li><a href="<%=main.cp%>/personal/index.jsp">Your account</a>
<li><a href="<%=main.cp%>/personal/editUserFormSelf.jsp">Modify your account information</a>
</ul>
-->

<icd:RU/>

</body>
</html>
