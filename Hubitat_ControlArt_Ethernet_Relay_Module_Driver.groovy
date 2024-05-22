/**
 *  Hubitat - CA Driver by TRATO
 *
 *  Copyright 2024 VH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *        1.0 22/5/2024  - V.BETA 1 
 */
metadata {
  definition (name: "Controlart - Ethernet Relay Module", namespace: "TRATO", author: "TRATO", vid: "generic-contact") { 
        capability "Configuration"
        capability "Initialize" 
        capability "Refresh"
        capability "Switch"       
      
  }
      
  }

import groovy.json.JsonSlurper
import groovy.transform.Field
command "buscainputcount"
command "createchilds"
command "getfw"
command "getmac"
command "getstatus"

  preferences {
        input "device_IP_address", "text", title: "IP Address of Device", required: true, defaultValue: "192.168"   
        input "device_port", "number", title: "IP Port of Device", required: true, defaultValue: 4998
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "outputs", type: "string", title: "How many Relays " , defaultValue: 10
        input name: "inputs", type: "string", title: "How many Inputs " , defaultValue: 12
      

    input 'logInfo', 'bool', title: 'Show Info Logs?',  required: false, defaultValue: true
    input 'logWarn', 'bool', title: 'Show Warning Logs?', required: false, defaultValue: true
    input 'logDebug', 'bool', title: 'Show Debug Logs?', description: 'Only leave on when required', required: false, defaultValue: true
    input 'logTrace', 'bool', title: 'Show Detailed Logs?', description: 'Only leave on when required', required: false, defaultValue: true

    //attribute "powerstatus", "string"
    attribute "boardstatus", "string"
      
  }   


@Field static String partialMessage = ''
@Field static Integer checkInterval = 600


def installed() {
    logTrace('installed()')
    updated()
    def novaprimeira = ""
    def oldprimeira = ""
    runIn(1800, logsOff)
}

def uninstalled() {
    logTrace('uninstalled()')
    unschedule()
    interfaces.rawSocket.close()
}

def updated() {
    logTrace('updated()')
    initialize()
    refresh()
}

def configure() {
    logTrace('configure()')
    state.inputcount = 12
    state.outputcount = 10      
    unschedule()
}



def initialize() {
    logTrace('initialize()')
    unschedule()
    interfaces.rawSocket.close();
    state.clear()
    def novaprimeira = ""
    def oldprimeira = ""
    partialMessage = '';

    if (!device_IP_address) {
        logError 'IP do Device not configured'
        return
    }

    if (!device_port) {
        logError 'Porta do Device não configurada.'
        return
    }
    //Llama la busca de count de inputs+outputs

    
    try {
        logTrace("tentando conexão com o device no ${device_IP_address}...na porta ${device_port}");
        int i = 4998
        interfaces.rawSocket.connect(device_IP_address, (int) device_port);
        state.lastMessageReceivedAt = now();
        getmac()
        runIn(15, "getstatus");
        runIn(checkInterval, "connectionCheck");
        refresh();  // se estava offline, preciso fazer um refresh
    }
    catch (e) {
        logError( "${device_IP_address} initialize error: ${e.message}" )
         sendEvent(name: "boardstatus", value: "offline", isStateChange: true)        

        runIn(60, "initialize");
    }
    createchilds()
       
}


def createchilds() {
    state.inputcount = 12
    state.outputcount = 10    
    String thisId = device.id
	log.info "info thisid " + thisId
	def cd = getChildDevice("${thisId}-Switch")
    state.netids = "${thisId}-Switch-"
	if (!cd) {
        log.info "outputcount = " + state.outputcount 
        for(int i = 1; i<=state.outputcount; i++) {        
        cd = addChildDevice("hubitat", "Generic Component Switch", "${thisId}-Switch-" + Integer.toString(i), [name: "${device.displayName} Switch-" + Integer.toString(i) , isComponent: true])
        log.info "added switch # " + i + " from " + state.outputcount            
        
    }
    }         
}

def getstatus()
{
   
    def msg = "mdcmd_getmd," + state.macaddress
    //def msg = "mdcmd_getmd," + state.macaddress + "\r\n"
    sendCommand(msg)
    //sendCommand(msg)
    
}


def getmac(){
    def msg = "get_mac_addr" 
    //def msg = "get_mac_addr\r\n" 
    sendCommand(msg)

}

def getfw()
{
    def msg = "get_firmware_version\r\n"
    sendCommand(msg)

}

def refresh() {
    def msg = "mdcmd_getmd," + state.macaddress 
    sendCommand(msg)
}


//Feedback e o tratamento 

 def fetchChild(String type, String name){
    String thisId = device.id
    def cd = getChildDevice("${thisId}-${type}_${name}")
    if (!cd) {
        cd = addChildDevice("hubitat", "Generic Component ${type}", "${thisId}-${type}_${name}", [name: "${name}", isComponent: true])
        cd.parse([[name:"switch", value:"off", descriptionText:"set initial switch value"]]) //TEST!!
    }
    return cd 
}


def parse(msg) {
    state.lastMessageReceived = new Date(now()).toString();
    state.lastMessageReceivedAt = now();
    
    
    def oldmessage = state.lastmessage

    
    def newmsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) //na Mol, o resultado vem em HEX, então preciso converter para Array
    def newmsg2 = new String(newmsg) // Array para String
    
    
    state.lastmessage = newmsg2
    
    //firmware
    if (newmsg2.length() < 7) {
        state.firmware = newmsg2
        log.info "FW = " + newmsg2
        //sendEvent(name: "boardstatus", value: "online")
    }

    //macadress
    if (newmsg2.contains("macaddr")){
        def mac = newmsg2
        def newmac = (mac.substring(10)); 
        newmac = (newmac.replaceAll(",","0x")); 
        newmac = (newmac.replaceAll("-",",0x")); 
        newmac = newmac.replaceAll("\\s","")

        state.macaddress = newmac
        log.info "MAC " + newmac 
        sendEvent(name: "boardstatus", value: "online")
    }
    
    //getstatus
    if (newmsg2.contains("setcmd")){
        sendEvent(name: "boardstatus", value: "online")
        def oldprimeira = state.primeira  
        state.lastprimeira =  state.primeira //salvo o valor anterior

        //tratamento para pegar os inputs e outputs 
        newmsg2 = newmsg2[16..60]
        varinputs = newmsg2[0..22]
        varinputs = varinputs.replaceAll(",","")
        state.inputs  = varinputs
        varoutputs = newmsg2[24..44]
        varoutputs =  varoutputs.replaceAll(",","")
        state.outputs =  varoutputs
        novaprimeira = varoutputs
        state.primeira = novaprimeira
        
        if ((novaprimeira)&&(oldprimeira)) {  //if not empty in first run
        
            if (novaprimeira.compareToIgnoreCase(oldprimeira) == 0){
            
                log.info "No changes in relay status"
                
            }
            
            else{
              
            for(int f = 0; f <state.outputcount; f++) {  
            def valprimeira = state.primeira[f]
            def valold = oldprimeira[f]
            def diferenca = valold.compareToIgnoreCase(valprimeira)            
                
                log.info "valor valprimeira = " + valprimeira + " , valor valold = " + valold + " , valdiferenca = " + diferenca
                switch(diferenca) { 
                 case 0: 
                   log.info "no changes in ch#" + (f+1) ;
                 break                     
                
                 case -1:  //  -1 is when light was turned ON                   
                   if (state.update == 1){     //no hace nada porque fue el switch
                   log.info "(NOTHING) - ON changes in ch#" + (f+1) ;
                   }
                   else {

                    chdid = state.netids + (f+1) 
                    def cd = getChildDevice(chdid)
                    log.info "ON changes in ch#" + (f+1) +  ", chdid = " + chdidchdid + ", cd = " + cd   
                    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
                    //buscar y cambiar el status a ON del switch
                        }
                 break
                
                 case 1: // 1 is when light was turned OFF
                    log.info "OFF changes in ch#" + (f+1) ;
                    chdid = state.netids + (f+1) 
                    def cd = getChildDevice(chdid) 
                    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                   
                    //buscar y cambiar el status a OFF del switch
    
                 break          
                
                default:
                log.info "changes in ch#" + (f+1) + " with dif " + diferenca;
                break                    
            
            
               }//switch
        
          }//for 
                    state.update = 0  

        state.primeira = state.outputs
        log.info "Status " + newmsg2 
        log.info "Outputs " + state.outputs
        log.info "Inputs " + state.inputs        
        
        
          } //else hubo cambios
    
         //log.info "status do update = " + state.update      
} //fechou 

    }
} 
    
////////////////
////Commands 
////////////////

def on()
{
    logDebug("Master Power ON()")
    def msg = "mdcmd_setallonmd," + state.macaddress + "\r\n"
    sendCommand(msg)
    pauseexecution(700)
    getstatus()
    
}


def off()
{
    logDebug("Master Power OFF()")
    def msg = "mdcmd_setmasteroffmd," + state.macaddress + "\r\n"
    sendCommand(msg)
    pauseexecution(700)
    getstatus()
    
}


private sendCommand(s) {
    logDebug("sendCommand ${s}")
    interfaces.rawSocket.sendMessage(s)    
}


////////////////////////////
//// Connections Checks ////
////////////////////////////

def connectionCheck() {
    def now = now();
    
    if ( now - state.lastMessageReceivedAt > (checkInterval * 1000)) { 
        logError("sem mensagens desde ${(now - state.lastMessageReceivedAt)/60000} minutos, reconectando ...");
        initialize();
    }
    else {
        logDebug("connectionCheck ok");
         sendEvent(name: "boardstatus", value: "online")
        runIn(checkInterval, "connectionCheck");
    }
}

def socketStatus(String message) {
    if (message == "receive error: String index out of range: -1") {
        // This is some error condition that repeats every 15ms.

        interfaces.rawSocket.close();       
        logError( "socketStatus: ${message}");
        logError( "Closing connection to device" );
    }
    else if (message != "receive error: Read timed out") {
        logError( "socketStatus: ${message}")
    }
}




/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Component Child
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

void componentRefresh(cd){
	if (logEnable) log.info "received refresh request from ${cd.displayName}"
	refresh()
    
    
}

def componentOn(cd){
	if (logEnable) log.info "received on request from ${cd.displayName}"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])       
    on(cd)  
    pauseExecution(300)
    //getstatus()
    
}

void componentOff(cd){
	if (logEnable) log.info "received off request from ${cd.displayName}"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])    
	off(cd)
    pauseExecution(300)
    //getstatus()

}


////// Driver Commands /////////


//SEND ON COMMAND IN CHILD BUTTON
void on(cd) {
if (logEnable) log.debug "Turn device ON"	
sendEvent(name: "switch", value: "on", isStateChange: true)
//cd.updateDataValue("Power","On")    
//cd.parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])

ipdomodulo  = state.ipaddress
lengthvar =  (cd.deviceNetworkId.length())
int relay = 0
if (lengthvar < 13) {
    def numervalue1 = (cd.deviceNetworkId as String)[11]
    def valor = ""
    valor =   numervalue1 as Integer
    relay = valor   
    }

    else 
   
{
    def numervalue2 = (cd.deviceNetworkId as String)[12] 
    def valor = ""
    valor =   numervalue2 as Integer
    relay = valor 
}
     def stringrelay = relay
     def comando = "mdcmd_sendrele," +state.macaddress+ "," + stringrelay + ",1\r\n"
     interfaces.rawSocket.sendMessage(comando)
     log.info "Foi Ligado o Relay " + relay + " via TCP " + comando 
     sendEvent(name: "power", value: "on")
     state.update = 1  //variable to control update with board on parse
    
}


//SEND OFF COMMAND IN CHILD BUTTON 
void off(cd) {
if (logEnable) log.debug "Turn device OFF"	
sendEvent(name: "switch", value: "off", isStateChange: true)
cd.updateDataValue("Power","Off")
//cd.parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
    
ipdomodulo  = state.ipaddress
lengthvar =  (cd.deviceNetworkId.length())
int relay = 0
if (lengthvar < 13) {
    def numervalue1 = (cd.deviceNetworkId as String)[11]
    def valor = ""
    valor =   numervalue1 as Integer
    relay = valor   
    }

    else 
    
{
    def numervalue2 = (cd.deviceNetworkId as String)[12] 
    def valor = ""
    valor =   numervalue2 as Integer
    relay = valor 
}
     def stringrelay = relay
     def comando = "mdcmd_sendrele," +state.macaddress+ "," + stringrelay + ",0\r\n"
     interfaces.rawSocket.sendMessage(comando)
     log.info "Foi Desligado o Relay " + relay + " via TCP " + comando 
     state.update = 1    //variable to control update with board on parse
    
}




////////////////////////////////////////////////
////////LOGGING
///////////////////////////////////////////////


private processEvent( Variable, Value ) {
    if ( state."${ Variable }" != Value ) {
        state."${ Variable }" = Value
        logDebug( "Event: ${ Variable } = ${ Value }" )
        sendEvent( name: "${ Variable }", value: Value, isStateChanged: true )
    }
}



def logsOff() {
    log.warn 'logging disabled...'
    device.updateSetting('logInfo', [value:'false', type:'bool'])
    device.updateSetting('logWarn', [value:'false', type:'bool'])
    device.updateSetting('logDebug', [value:'false', type:'bool'])
    device.updateSetting('logTrace', [value:'false', type:'bool'])
}

void logDebug(String msg) {
    if ((Boolean)settings.logDebug != false) {
        log.debug "${drvThis}: ${msg}"
    }
}

void logInfo(String msg) {
    if ((Boolean)settings.logInfo != false) {
        log.info "${drvThis}: ${msg}"
    }
}

void logTrace(String msg) {
    if ((Boolean)settings.logTrace != false) {
        log.trace "${drvThis}: ${msg}"
    }
}

void logWarn(String msg, boolean force = false) {
    if (force || (Boolean)settings.logWarn != false) {
        log.warn "${drvThis}: ${msg}"
    }
}

void logError(String msg) {
    log.error "${drvThis}: ${msg}"
}

