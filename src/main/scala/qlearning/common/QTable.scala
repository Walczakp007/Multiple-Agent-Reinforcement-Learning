/** Map from states to possible moves and their values.
  * For each move transition (T), there will be a floating point number which is an estimate of the value
  * of making that particular move. Initially all those values are 0.
  *
  * A Qtable works well when the size of the space is relatively small, but for more complex games and puzzles,
  * like go for example, we need to use a model like a deep neural net to approximate the total space.
  *
  * @param initialState starting state (optional). If provided, then all other states are inferred from this one.
  * @param theTable (Optional) table of all possible states and possible transitions from them.
  *            If not provided, then all states will be inferred from the starting state.
  * @param epsilon percent of the time to make a random transition instead of make the best one
  * @param rnd used for deterministic unit tests.
  * @author Barry Becker
  */

package qlearning.common

import scala.collection.mutable
import scala.util.Random
import qlearning.common.QTable._


object QTable {

  private val RND = new Random((Math.random() * 10000).toLong)
  private val DEFAULT_EPS = 0.01
  private val EPS_DROPOFF = 5.0f

}

case class QTable[T](initialState: State[T],
                     theTable: Option[Map[State[T], mutable.Map[T, Float]]],
                     epsilon: Double = DEFAULT_EPS, rnd: Random = RND) {

  val table: Map[State[T], mutable.Map[T, Float]] =
    if (theTable.isEmpty) createInitializedTable(initialState) else theTable.get


  def getBestMove(b: State[T]): (T, Float) = {
    val actionsList = table(b).toSeq
    b.selectBestAction(actionsList, rnd)
  }

  def getPossibleActions(b: State[T]): List[(T, Float)] = {
    val actions = table(b)
    val actionList: List[(T, Float)] =
      actions.toList.map(entry => (entry._1, entry._2))
    actionList
  }

  def getNextAction(b: State[T], episodeNumber: Int): (T, Float) = {
    val actions = table(b)
    val actionList: List[(T, Float)] =
      actions.toList.map(entry => (entry._1, entry._2))

    val eps = epsilon + EPS_DROPOFF / (episodeNumber + EPS_DROPOFF)
    if (rnd.nextDouble() < eps) actionList(rnd.nextInt(actionList.length)) // purely random action
    else b.selectBestAction(actionList, rnd) // select randomly from actions with best value
  }

  def update(state: State[T], action: (T, Float), nextState: State[T],
             learningRate: Float, futureRewardDiscount: Float = 1.0f): Unit = {
    val nextActions = table(nextState)
    val futureValue = if (nextActions.isEmpty) 0.0f else nextState.selectBestAction(nextActions.toSeq, rnd)._2
    val reward = nextState.rewardForLastMove
    val newValue = action._2 + learningRate * ((reward + futureRewardDiscount * futureValue) - action._2)
    table(state) += (action._1 -> newValue)  // update
  }

  def getActions(b: State[T]): mutable.Map[T, Float] = table(b)

  def getFirstNEntriesWithNon0Actions(n: Int): String =
    table.filter(e => e._2.values.sum > 0.0f).take(n).mkString("\n")

  private def createInitializedTable(initialState: State[T]): Map[State[T], mutable.Map[T, Float]] = {
    val table = mutable.Map[State[T], mutable.Map[T, Float]]()
    traverse(initialState, table)
    table.map(entry => (entry._1, entry._2)).toMap // make immutable
  }

  private def traverse(currentState: State[T],
                       table: mutable.Map[State[T], mutable.Map[T, Float]]): Unit = {
    if (!table.contains(currentState)) {
      val possibleMoves = currentState.getLegalTransitions
      val moves: mutable.Map[T, Float] = mutable.Map(possibleMoves.map(m => (m, 0.0f)): _*)
      table(currentState) = moves
      for (position <- moves.keys) {
        traverse(currentState.makeTransition(position), table)
      }
    }
  }

  override def toString: String = "numEntries=" + table.size
}