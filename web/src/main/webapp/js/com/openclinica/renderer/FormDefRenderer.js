function FormDefRenderer(json) {
  this.json = json;
  this.name = json["@Name"];
  this.OID = json["@OID"];
  this.repeating = ParseUtil.parseYesNo(json["@Repeating"]);
  this.renderPrintableForm = function() { return RenderUtil.render(RenderUtil.get("print_form_def"), {name: this.name});}
  this.renderInteractiveForm = function() { return RenderUtil.render(RenderUtil.get("e_form_def"), {name: this.name});}
  this.eventCRFdns=undefined;
  this.eventCRFaudits=undefined;
  
  
    
  this.renderDiscrepancyNotes = function(discrepancyNotes){
    var template="print_EventCRF_StudyEvent_StudySubject_dns";
    var title = "Event CRF Notes & Discrepancies" ;
	var s = RenderUtil.render(RenderUtil.get(template),{discrepancyNotes:discrepancyNotes, title:title } );
    return s[0].outerHTML;
  }
  
this.renderAuditLogs = function(auditLogs){
    var template="print_EventCRF_StudyEvent_StudySubject_audits";
    var title = "Event CRF Audit History" ;
    var s = RenderUtil.render(RenderUtil.get(template),{auditLogs:auditLogs , title:title });
    return s[0].outerHTML;
  }

  

  }
  
