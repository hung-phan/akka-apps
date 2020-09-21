package domain.model

import domain.common.{Entity, ID, MsgType}
import domain.model.UserModel.UserEntity

object ChatModel {
  sealed trait ChatLogEntity extends Entity[String] {
    type MessageType

    val from: UserEntity
    val msg: MsgType[MessageType]
  }

  case class TextChatLog(id: ID[String], msg: MsgType[String], from: UserEntity)
      extends ChatLogEntity {
    override type MessageType = String
  }

  val MAX_NUMBER_OF_LAST_MESSAGES = 20

  case class ChatState(
      id: ID[String],
      users: Set[UserEntity],
      previousMsgs: List[ChatLogEntity],
      lastMsgs: List[ChatLogEntity]
  ) extends Entity[String] {
    def addUser(user: UserEntity): ChatState =
      this.copy(users = users + user)

    def removeUser(user: UserEntity): ChatState =
      this.copy(users = users - user)

    def appendMsg(msg: ChatLogEntity): ChatState = {
      if (lastMsgs.length == MAX_NUMBER_OF_LAST_MESSAGES) {
        return this.copy(
          previousMsgs = previousMsgs :+ lastMsgs.head,
          lastMsgs = lastMsgs.tail :+ msg
        )
      }

      this.copy(lastMsgs = lastMsgs :+ msg)
    }
  }
}
