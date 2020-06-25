package domain.model

import domain.common.Entity
import domain.model.User.UserEntity

object Chat {
  sealed trait ChatLogEntity extends Entity {
    type MsgType
    val msg: MsgType
  }

  case class TextChatLog(id: String, msg: String, from: UserEntity)
      extends ChatLogEntity {
    type ID = String
    type MsgType = String
  }

  val MAX_NUMBER_OF_LAST_MESSAGES = 20

  case class ChatState(id: String,
                       users: Set[UserEntity],
                       previousMsgs: List[ChatLogEntity],
                       lastMsgs: List[ChatLogEntity])
      extends Entity {
    override type ID = String

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
