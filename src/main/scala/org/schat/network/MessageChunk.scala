package org.schat.network

import java.nio.ByteBuffer

import scala.collection.mutable.ArrayBuffer

private[network] class MessageChunk(val header: MessageChunkHeader, val buffer: ByteBuffer) {}

