package org.schat.network


private[schat] case class ConnectionId(connectionManagerId: ConnectionManagerId, uniqId: Int) {
        override def toString = connectionManagerId.host + "_" + connectionManagerId.port +  "_" + uniqId
}

private[schat] object ConnectionId {
        def createConnectionIdFromString ( connectionIdString: String) : ConnectionId = {
            val res = connectionIdString.split("_").map(_.trim())
            if (res.size != 3) {
                  throw new Exception("Error converting ConnectionId string: " + connectionIdString + " to a ConnectionId Object")
    
            }
            new ConnectionId(new ConnectionManagerId(res(0), res(1).toInt), res(2).toInt)
        }
} 
