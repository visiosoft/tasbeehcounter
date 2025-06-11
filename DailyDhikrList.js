import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Dimensions,
} from 'react-native';

const dhikrList = [
  {
    id: 1,
    arabic: 'سُبْحَانَ اللَّهِ',
    translation: 'Glory be to Allah',
    count: 33,
  },
  {
    id: 2,
    arabic: 'الْحَمْدُ لِلَّهِ',
    translation: 'All praise is due to Allah',
    count: 33,
  },
  {
    id: 3,
    arabic: 'اللَّهُ أَكْبَرُ',
    translation: 'Allah is the Greatest',
    count: 33,
  },
  {
    id: 4,
    arabic: 'لَا إِلَٰهَ إِلَّا اللَّهُ',
    translation: 'There is no god but Allah',
    count: 100,
  },
];

const DailyDhikrList = () => {
  const [progress, setProgress] = useState(
    dhikrList.reduce((acc, dhikr) => {
      acc[dhikr.id] = 0;
      return acc;
    }, {})
  );

  const incrementCount = (id) => {
    setProgress((prev) => ({
      ...prev,
      [id]: Math.min(prev[id] + 1, dhikrList.find((d) => d.id === id).count),
    }));
  };

  const resetCount = (id) => {
    setProgress((prev) => ({
      ...prev,
      [id]: 0,
    }));
  };

  const DhikrCard = ({ dhikr }) => {
    const isComplete = progress[dhikr.id] === dhikr.count;
    
    return (
      <View style={styles.card}>
        <Text style={styles.arabicText}>{dhikr.arabic}</Text>
        <Text style={styles.translationText}>{dhikr.translation}</Text>
        <View style={styles.progressContainer}>
          <Text style={styles.countText}>
            {progress[dhikr.id]}/{dhikr.count}
          </Text>
          <View style={styles.buttonContainer}>
            <TouchableOpacity
              style={[styles.button, styles.incrementButton]}
              onPress={() => incrementCount(dhikr.id)}
              disabled={isComplete}
            >
              <Text style={styles.buttonText}>+</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, styles.resetButton]}
              onPress={() => resetCount(dhikr.id)}
            >
              <Text style={styles.buttonText}>Reset</Text>
            </TouchableOpacity>
          </View>
        </View>
        {isComplete && (
          <Text style={styles.completeText}>✓ Completed</Text>
        )}
      </View>
    );
  };

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Daily Dhikr</Text>
      {dhikrList.map((dhikr) => (
        <DhikrCard key={dhikr.id} dhikr={dhikr} />
      ))}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 16,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 20,
    textAlign: 'center',
  },
  card: {
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  arabicText: {
    fontSize: 24,
    textAlign: 'center',
    marginBottom: 8,
    color: '#2c3e50',
  },
  translationText: {
    fontSize: 16,
    textAlign: 'center',
    color: '#7f8c8d',
    marginBottom: 16,
  },
  progressContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 8,
  },
  countText: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#2c3e50',
  },
  buttonContainer: {
    flexDirection: 'row',
    gap: 8,
  },
  button: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  incrementButton: {
    backgroundColor: '#3498db',
  },
  resetButton: {
    backgroundColor: '#e74c3c',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
  completeText: {
    color: '#27ae60',
    textAlign: 'center',
    marginTop: 8,
    fontWeight: 'bold',
  },
});

export default DailyDhikrList; 